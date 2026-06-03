package com.commspurx.mobile.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commspurx.mobile.data.local.BadgeCounts
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.model.canAccessApprovals
import com.commspurx.mobile.data.repository.ApprovalsRepository
import com.commspurx.mobile.data.repository.AuthRepository
import com.commspurx.mobile.data.repository.DeliveriesRepository
import com.commspurx.mobile.data.repository.NotificationsRepository
import com.commspurx.mobile.data.repository.PurchaseContractsRepository
import com.commspurx.mobile.data.repository.SalesContractsRepository
import com.commspurx.mobile.notifications.MonitorState
import com.commspurx.mobile.ui.main.MainTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HubUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val unreadNotifications: Int = 0,
    val pendingApprovals: Int = 0,
    val pendingDeliveries: Int = 0,
    val notificationBadgeCount: Int = 0,
    val notificationBadgeLabel: String = "",
    val hasUrgentNotifications: Boolean = false,
    val deliveryBadgeCount: Int = 0,
    val purchaseBadgeCount: Int = 0,
    val purchaseBadgeLabel: String = "",
    val salesBadgeCount: Int = 0,
    val salesBadgeLabel: String = "",
    val purchaseExpiringCount: Int = 0,
    val salesExpiringCount: Int = 0,
    val showLogoutConfirm: Boolean = false,
) {
    val showApprovalsBadge: Boolean =
        UserRole.from(user.role)?.canAccessApprovals() == true
}

class HubViewModel(
    user: AuthUser,
    account: AuthAccount?,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val approvalsRepository: ApprovalsRepository,
    private val deliveriesRepository: DeliveriesRepository,
    private val purchaseContractsRepository: PurchaseContractsRepository,
    private val salesContractsRepository: SalesContractsRepository,
    private val badgeStore: BadgeStore,
    private val stopNotificationMonitor: () -> Unit,
    private val onDataSynced: () -> Unit,
    private val onLoggedOut: () -> Unit,
) : ViewModel() {
    private val accountId = user.accountId
    private val _uiState = MutableStateFlow(HubUiState(user = user, account = account))
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    private var currentDeliveryIds: List<String> = emptyList()
    private var notificationIds: List<String> = emptyList()
    private var hasUrgentNotifications: Boolean = false
    private var approvalKeys: List<String> = emptyList()
    private var cachedApprovals: List<ApprovalItem> = emptyList()
    private var purchaseTotal = 0
    private var salesTotal = 0

    init {
        observeCachedData()
        observeMonitorSnapshots()
        syncInBackground()
    }

    fun refreshCounts() {
        syncInBackground()
    }

    fun refreshBadgeDisplay() {
        viewModelScope.launch { applyBadgeCounts() }
    }

    fun markDeliveriesSectionOpened() {
        viewModelScope.launch {
            badgeStore.markHubDeliveriesSeen(currentDeliveryIds)
            applyBadgeCounts()
        }
    }

    fun markPurchaseSectionOpened() {
        viewModelScope.launch {
            badgeStore.markMainTabPurchaseSeen(purchaseTotal)
            applyBadgeCounts()
        }
    }

    fun markSalesSectionOpened() {
        viewModelScope.launch {
            badgeStore.markMainTabSalesSeen(salesTotal)
            applyBadgeCounts()
        }
    }

    fun markNotificationsSectionOpened() {
        viewModelScope.launch {
            val approvalKeysList = if (_uiState.value.showApprovalsBadge) approvalKeys else emptyList()
            badgeStore.markHubNotificationsSeen(
                notificationIds = notificationIds,
                approvalKeys = approvalKeysList,
            )
            applyBadgeCounts()
        }
    }

    fun markMainTabVisited(tab: MainTab) {
        when (tab) {
            MainTab.Deliveries -> markDeliveriesSectionOpened()
            MainTab.Purchase -> markPurchaseSectionOpened()
            MainTab.Sales -> markSalesSectionOpened()
            MainTab.Notifications -> markNotificationsSectionOpened()
        }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirm = true) }
    }

    fun dismissLogoutConfirm() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
    }

    fun confirmLogout() {
        viewModelScope.launch {
            _uiState.update { it.copy(showLogoutConfirm = false) }
            badgeStore.clear()
            stopNotificationMonitor()
            authRepository.logout()
            onLoggedOut()
        }
    }

    private fun observeCachedData() {
        viewModelScope.launch {
            notificationsRepository.observeUnread(accountId).collect { notifications ->
                applyNotificationSnapshot(notifications)
            }
        }
        viewModelScope.launch {
            deliveriesRepository.observeCurrent(accountId).collect { deliveries ->
                currentDeliveryIds = deliveries.map { it.id }
                _uiState.update {
                    it.copy(pendingDeliveries = deliveries.size)
                }
                applyBadgeCounts()
            }
        }
        viewModelScope.launch {
            combine(
                purchaseContractsRepository.totalExpiring,
                salesContractsRepository.totalExpiring,
            ) { purchase, sales -> purchase to sales }
                .collect { (purchase, sales) ->
                    purchaseTotal = purchase
                    salesTotal = sales
                    _uiState.update {
                        it.copy(
                            purchaseExpiringCount = purchase,
                            salesExpiringCount = sales,
                        )
                    }
                    applyBadgeCounts()
                }
        }
    }

    private fun observeMonitorSnapshots() {
        viewModelScope.launch {
            MonitorState.snapshots.collect { snapshot ->
                applyNotificationSnapshot(snapshot.notifications)
                if (_uiState.value.showApprovalsBadge) {
                    cacheBadgeSources(
                        notifications = snapshot.notifications,
                        approvals = snapshot.approvals,
                        deliveryIds = snapshot.pendingDeliveries
                            .filter { delivery -> delivery.status != "completed" }
                            .map { it.id },
                    )
                    purchaseTotal = snapshot.pendingPurchaseContracts.size
                    salesTotal = snapshot.pendingSalesContracts.size
                    _uiState.update {
                        it.copy(
                            pendingApprovals = snapshot.approvals.size,
                            pendingDeliveries = snapshot.pendingDeliveries.size,
                            purchaseExpiringCount = purchaseTotal,
                            salesExpiringCount = salesTotal,
                        )
                    }
                } else {
                    purchaseTotal = snapshot.pendingPurchaseContracts.size
                    salesTotal = snapshot.pendingSalesContracts.size
                    _uiState.update {
                        it.copy(
                            purchaseExpiringCount = purchaseTotal,
                            salesExpiringCount = salesTotal,
                        )
                    }
                }
                applyBadgeCounts()
            }
        }
    }

    private fun syncInBackground() {
        viewModelScope.launch {
            runCatching { notificationsRepository.listUnread(accountId) }
            if (_uiState.value.showApprovalsBadge) {
                cachedApprovals = approvalsRepository.listPending()
                approvalKeys = cachedApprovals.map { BadgeStore.approvalKey(it.entityType, it.id) }
                _uiState.update {
                    it.copy(pendingApprovals = cachedApprovals.size)
                }
            }
            runCatching { deliveriesRepository.listCurrent(accountId) }
            runCatching { purchaseContractsRepository.load(accountId) }
            runCatching { salesContractsRepository.load(accountId) }
            onDataSynced()
            applyBadgeCounts()
        }
    }

    private fun applyNotificationSnapshot(notifications: List<NotificationItem>) {
        val approvals = if (_uiState.value.showApprovalsBadge) {
            cachedApprovals
        } else {
            emptyList()
        }
        cacheBadgeSources(notifications, approvals, currentDeliveryIds)
        _uiState.update {
            it.copy(
                unreadNotifications = notifications.size,
                pendingApprovals = approvals.size,
                pendingDeliveries = currentDeliveryIds.size,
            )
        }
        viewModelScope.launch { applyBadgeCounts() }
    }

    private fun cacheBadgeSources(
        notifications: List<NotificationItem>,
        approvals: List<ApprovalItem>,
        deliveryIds: List<String>,
    ) {
        notificationIds = notifications.map { it.id }
        hasUrgentNotifications = notifications.any { it.isHighPriority }
        approvalKeys = approvals.map { BadgeStore.approvalKey(it.entityType, it.id) }
        currentDeliveryIds = deliveryIds
    }

    private suspend fun applyBadgeCounts() {
        val state = _uiState.value
        val approvalList = if (state.showApprovalsBadge) approvalKeys else emptyList()
        val notifUnseen = badgeStore.hubNotificationUnseen(notificationIds, approvalList)
        val purchaseUnseen = badgeStore.mainTabPurchaseUnseen(purchaseTotal)
        val salesUnseen = badgeStore.mainTabSalesUnseen(salesTotal)
        _uiState.update {
            it.copy(
                notificationBadgeCount = BadgeCounts.badgeValue(notifUnseen),
                notificationBadgeLabel = BadgeCounts.badgeLabel(notifUnseen),
                hasUrgentNotifications = hasUrgentNotifications,
                deliveryBadgeCount = badgeStore.hubDeliveryBadge(currentDeliveryIds),
                purchaseBadgeCount = BadgeCounts.badgeValue(purchaseUnseen),
                purchaseBadgeLabel = BadgeCounts.badgeLabel(purchaseUnseen),
                salesBadgeCount = BadgeCounts.badgeValue(salesUnseen),
                salesBadgeLabel = BadgeCounts.badgeLabel(salesUnseen),
            )
        }
    }
}
