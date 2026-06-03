package com.commspurx.mobile.data.repository

import com.commspurx.mobile.data.api.ContractsApi
import com.commspurx.mobile.data.api.DeliveriesApi
import com.commspurx.mobile.data.api.NotificationsApi
import com.commspurx.mobile.data.local.CommspurxDatabase
import com.commspurx.mobile.data.local.toEntity
import com.commspurx.mobile.data.local.toItem
import com.commspurx.mobile.data.local.toPurchaseEntity
import com.commspurx.mobile.data.local.toSalesEntity
import com.commspurx.mobile.data.local.toSummary
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.isCompleted
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.network.ConnectivityMonitor
import com.commspurx.mobile.data.local.BadgeCounts
import com.commspurx.mobile.data.model.PaginationMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PurchaseContractsRepository(
    private val contractsApi: ContractsApi,
    private val database: CommspurxDatabase,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    private val refreshMutex = Mutex()
    private val _totalExpiring = MutableStateFlow(0)
    val totalExpiring: StateFlow<Int> = _totalExpiring.asStateFlow()

    fun observeExpiring(accountId: String): Flow<List<ContractSummary>> =
        database.purchaseContractDao().observeAll(accountId).map { rows ->
            rows.map { it.toSummary() }
        }

    suspend fun refresh(accountId: String, days: Int = 7): ContractSyncResult =
        refreshMutex.withLock {
            if (!canSync()) return ContractSyncResult(success = false)
            withContext(Dispatchers.IO) {
                try {
                    val remote = contractsApi.listPurchaseExpiringSoon(
                        days = days,
                        page = 1,
                        pageSize = BadgeCounts.BACKGROUND_SYNC_CAP,
                    )
                    val total = remote.meta?.totalItems ?: remote.data.size
                    _totalExpiring.value = total
                    val now = System.currentTimeMillis()
                    val entities = remote.data.map { row ->
                        row.toSummary().toPurchaseEntity(accountId, now, row.seller.orEmpty())
                    }
                    database.purchaseContractDao().replaceAll(accountId, entities)
                    connectivityMonitor.noteBackendReachable()
                    ContractSyncResult(success = true, truncated = remote.truncated, totalItems = total)
                } catch (_: Exception) {
                    ContractSyncResult(success = false)
                }
            }
        }

    suspend fun loadPage(
        accountId: String,
        page: Int,
        days: Int = 7,
        pageSize: Int = BadgeCounts.MOBILE_PAGE_SIZE,
    ): Pair<List<ContractSummary>, PaginationMeta?> = withContext(Dispatchers.IO) {
        if (!canSync()) return@withContext readCached(accountId) to null
        try {
            val remote = contractsApi.listPurchaseExpiringSoon(days, page, pageSize)
            val total = remote.meta?.totalItems ?: remote.data.size
            if (page == 1) _totalExpiring.value = total
            val now = System.currentTimeMillis()
            val entities = remote.data.map { row ->
                row.toSummary().toPurchaseEntity(accountId, now, row.seller.orEmpty())
            }
            if (page == 1) {
                database.purchaseContractDao().replaceAll(accountId, entities)
            } else {
                database.purchaseContractDao().insertAll(entities)
            }
            connectivityMonitor.noteBackendReachable()
            remote.data.map { it.toSummary() } to remote.meta
        } catch (_: Exception) {
            readCached(accountId) to null
        }
    }

    suspend fun load(accountId: String, days: Int = 7): List<ContractSummary> {
        val (rows, _) = loadPage(accountId, page = 1, days = days)
        return rows
    }

    private suspend fun readCached(accountId: String): List<ContractSummary> =
        database.purchaseContractDao().getAll(accountId).map { it.toSummary() }

    private fun canSync(): Boolean {
        val status = connectivityMonitor.backendStatus.value
        return connectivityMonitor.hasNetwork.value &&
            status != BackendConnectionStatus.Offline
    }
}

class SalesContractsRepository(
    private val contractsApi: ContractsApi,
    private val database: CommspurxDatabase,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    private val refreshMutex = Mutex()
    private val _totalExpiring = MutableStateFlow(0)
    val totalExpiring: StateFlow<Int> = _totalExpiring.asStateFlow()

    fun observeExpiring(accountId: String): Flow<List<ContractSummary>> =
        database.salesContractDao().observeAll(accountId).map { rows ->
            rows.map { it.toSummary() }
        }

    suspend fun refresh(accountId: String, days: Int = 7): ContractSyncResult =
        refreshMutex.withLock {
            if (!canSync()) return ContractSyncResult(success = false)
            withContext(Dispatchers.IO) {
                try {
                    val remote = contractsApi.listSalesExpiringSoon(
                        days = days,
                        page = 1,
                        pageSize = BadgeCounts.BACKGROUND_SYNC_CAP,
                    )
                    val total = remote.meta?.totalItems ?: remote.data.size
                    _totalExpiring.value = total
                    val now = System.currentTimeMillis()
                    val entities = remote.data.map { row ->
                        row.toSummary().toSalesEntity(accountId, now, row.buyer)
                    }
                    database.salesContractDao().replaceAll(accountId, entities)
                    connectivityMonitor.noteBackendReachable()
                    ContractSyncResult(success = true, truncated = remote.truncated, totalItems = total)
                } catch (_: Exception) {
                    ContractSyncResult(success = false)
                }
            }
        }

    suspend fun loadPage(
        accountId: String,
        page: Int,
        days: Int = 7,
        pageSize: Int = BadgeCounts.MOBILE_PAGE_SIZE,
    ): Pair<List<ContractSummary>, PaginationMeta?> = withContext(Dispatchers.IO) {
        if (!canSync()) return@withContext readCached(accountId) to null
        try {
            val remote = contractsApi.listSalesExpiringSoon(days, page, pageSize)
            val total = remote.meta?.totalItems ?: remote.data.size
            if (page == 1) _totalExpiring.value = total
            val now = System.currentTimeMillis()
            val entities = remote.data.map { row ->
                row.toSummary().toSalesEntity(accountId, now, row.buyer)
            }
            if (page == 1) {
                database.salesContractDao().replaceAll(accountId, entities)
            } else {
                database.salesContractDao().insertAll(entities)
            }
            connectivityMonitor.noteBackendReachable()
            remote.data.map { it.toSummary() } to remote.meta
        } catch (_: Exception) {
            readCached(accountId) to null
        }
    }

    suspend fun load(accountId: String, days: Int = 7): List<ContractSummary> {
        val (rows, _) = loadPage(accountId, page = 1, days = days)
        return rows
    }

    private suspend fun readCached(accountId: String): List<ContractSummary> =
        database.salesContractDao().getAll(accountId).map { it.toSummary() }

    private fun canSync(): Boolean {
        val status = connectivityMonitor.backendStatus.value
        return connectivityMonitor.hasNetwork.value &&
            status != BackendConnectionStatus.Offline
    }
}

class CachedNotificationsRepository(
    private val notificationsApi: NotificationsApi,
    private val database: CommspurxDatabase,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    private val refreshMutex = Mutex()
    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()
    fun observeAll(accountId: String): Flow<List<NotificationItem>> =
        database.notificationDao().observeAll(accountId).map { rows ->
            rows.map { it.toItem() }
        }

    suspend fun refresh(accountId: String): Boolean =
        refreshMutex.withLock {
            if (!canSync()) return false
            withContext(Dispatchers.IO) {
                try {
                    val remote = notificationsApi.listNotifications(
                        unread = null,
                        page = 1,
                        pageSize = BadgeCounts.MOBILE_PAGE_SIZE,
                    )
                    _unreadTotal.value = remote.unreadCount
                    val now = System.currentTimeMillis()
                    database.notificationDao().replaceAll(
                        accountId,
                        remote.data.map { it.toEntity(accountId, now) },
                    )
                    connectivityMonitor.noteBackendReachable()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

    suspend fun loadMore(accountId: String, page: Int): Boolean = withContext(Dispatchers.IO) {
        if (!canSync()) return@withContext false
        try {
            val remote = notificationsApi.listNotifications(
                unread = null,
                page = page,
                pageSize = BadgeCounts.MOBILE_PAGE_SIZE,
            )
            val now = System.currentTimeMillis()
            database.notificationDao().insertAll(
                remote.data.map { it.toEntity(accountId, now) },
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun observeUnread(accountId: String): Flow<List<NotificationItem>> =
        database.notificationDao().observeAll(accountId).map { rows ->
            rows.filter { it.readAt == null }.map { it.toItem() }
        }

    suspend fun listUnread(accountId: String): List<NotificationItem> {
        val cached = readUnread(accountId)
        if (canSync()) {
            runCatching { refresh(accountId) }
        }
        return readUnread(accountId).ifEmpty { cached }
    }

    suspend fun listAll(accountId: String): List<NotificationItem> {
        val cached = readAll(accountId)
        if (canSync()) {
            runCatching { refresh(accountId) }
        }
        return readAll(accountId).ifEmpty { cached }
    }

    private suspend fun readUnread(accountId: String): List<NotificationItem> =
        database.notificationDao().getAll(accountId).filter { it.readAt == null }.map { it.toItem() }

    private suspend fun readAll(accountId: String): List<NotificationItem> =
        database.notificationDao().getAll(accountId).map { it.toItem() }

    suspend fun markRead(accountId: String, id: String) {
        val readAt = java.time.Instant.now().toString()
        database.notificationDao().markReadLocal(accountId, id, readAt)
        if (canSync()) {
            try {
                notificationsApi.markRead(id)
                refresh(accountId)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun markAllRead(accountId: String) {
        val readAt = java.time.Instant.now().toString()
        database.notificationDao().markAllReadLocal(accountId, readAt)
        if (canSync()) {
            try {
                notificationsApi.markAllRead()
                refresh(accountId)
            } catch (_: Exception) {
            }
        }
    }

    private fun canSync(): Boolean =
        connectivityMonitor.hasNetwork.value
}

class CachedDeliveriesRepository(
    private val deliveriesApi: DeliveriesApi,
    private val database: CommspurxDatabase,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    private val refreshMutex = Mutex()
    fun observeCurrent(accountId: String): Flow<List<DeliveryItem>> =
        database.deliveryDao().observeByCompleted(accountId, completed = false).map { rows ->
            rows.map { it.toItem() }
        }

    fun observeCompleted(accountId: String): Flow<List<DeliveryItem>> =
        database.deliveryDao().observeByCompleted(accountId, completed = true).map { rows ->
            rows.map { it.toItem() }
        }

    suspend fun refresh(accountId: String): Boolean =
        refreshMutex.withLock {
            if (!canSync()) return@withLock false
            withContext(Dispatchers.IO) {
                try {
                    val current = deliveriesApi
                        .listDeliveries(tab = "current", status = "ongoing", pageSize = 100)
                        .data
                        .filter { !it.isCompleted() }
                    val completed = deliveriesApi
                        .listDeliveries(tab = "current", status = "completed", pageSize = 100)
                        .data
                        .filter { it.isCompleted() }
                    val now = System.currentTimeMillis()
                    val all = (current + completed).map { it.toEntity(accountId, now) }
                    database.deliveryDao().replaceAll(accountId, all)
                    connectivityMonitor.noteBackendReachable()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

    suspend fun listCurrent(accountId: String): List<DeliveryItem> {
        val cached = readCurrent(accountId)
        if (canSync()) {
            runCatching { refresh(accountId) }
        }
        return readCurrent(accountId).ifEmpty { cached }
    }

    suspend fun listCompleted(accountId: String): List<DeliveryItem> {
        val cached = readCompleted(accountId)
        if (canSync()) {
            runCatching { refresh(accountId) }
        }
        return readCompleted(accountId).ifEmpty { cached }
    }

    private suspend fun readCurrent(accountId: String): List<DeliveryItem> =
        database.deliveryDao().getByCompleted(accountId, completed = false).map { it.toItem() }

    private suspend fun readCompleted(accountId: String): List<DeliveryItem> =
        database.deliveryDao().getByCompleted(accountId, completed = true).map { it.toItem() }

    private fun canSync(): Boolean =
        connectivityMonitor.hasNetwork.value
}
