package com.commspurx.mobile.data.repository

import com.commspurx.mobile.data.api.ApprovalsApi
import com.commspurx.mobile.data.api.BulkImportApi
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.BulkImportJobResult
import com.commspurx.mobile.data.model.DecideApprovalRequest
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.NotificationItem
import kotlinx.coroutines.flow.Flow

class NotificationsRepository(
    private val cached: CachedNotificationsRepository,
) {
    fun observeUnread(accountId: String): Flow<List<NotificationItem>> =
        cached.observeUnread(accountId)

    suspend fun listUnread(accountId: String): List<NotificationItem> =
        cached.listUnread(accountId)

    suspend fun listAll(accountId: String): List<NotificationItem> =
        cached.listAll(accountId)

    suspend fun markRead(accountId: String, id: String) {
        cached.markRead(accountId, id)
    }

    suspend fun markAllRead(accountId: String) {
        cached.markAllRead(accountId)
    }

    suspend fun refresh(accountId: String): Boolean = cached.refresh(accountId)
}

class ApprovalsRepository(
    private val approvalsApi: ApprovalsApi,
) {
    suspend fun listPending(): List<ApprovalItem> =
        runCatching { approvalsApi.listPending().data }.getOrDefault(emptyList())

    suspend fun refreshPending(): Boolean =
        runCatching {
            approvalsApi.listPending()
            true
        }.getOrDefault(false)

    suspend fun decide(entityType: String, entityId: String, action: String) {
        approvalsApi.decide(entityType, entityId, DecideApprovalRequest(action))
    }
}

class BulkImportRepository(
    private val bulkImportApi: BulkImportApi,
) {
    suspend fun getJob(jobId: String): BulkImportJobResult =
        bulkImportApi.getJob(jobId)
}

class DeliveriesRepository(
    private val cached: CachedDeliveriesRepository,
) {
    fun observeCurrent(accountId: String): Flow<List<DeliveryItem>> =
        cached.observeCurrent(accountId)

    fun observeCompleted(accountId: String): Flow<List<DeliveryItem>> =
        cached.observeCompleted(accountId)

    suspend fun listCurrent(accountId: String): List<DeliveryItem> =
        cached.listCurrent(accountId)

    suspend fun listCompleted(accountId: String): List<DeliveryItem> =
        cached.listCompleted(accountId)

    suspend fun refresh(accountId: String): Boolean = cached.refresh(accountId)
}
