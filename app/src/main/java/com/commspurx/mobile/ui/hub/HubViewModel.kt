package com.commspurx.mobile.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HubUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val unreadNotifications: Int = 0,
    val pendingApprovals: Int = 0,
    val pendingDeliveries: Int = 0,
    val notificationBadgeCount: Int = 0,
    val hasUrgentNotifications: Boolean = false,
    val deliveryBadgeCount: Int = 0,
    val purchaseBadgeCount: Int = 0,
    val salesBadgeCount: Int = 0,
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
    private var purchaseContractIds: List<String> = emptyList()
    private var salesContractIds: List<String> = emptyList()
    private var notificationIds: List<String> = emptyList()
    private var hasUrgentNotifications: Boolean = false
    private var approvalKeys: List<String> = emptyList()
    private var cachedApprovals: List<ApprovalItem> = emptyList()

    init {
        observeCachedData()
        observeMonitorSnapshots()
        syncInBackground()
    }

    fun refreshCounts() {
        syncInBackground()
    }

    fun refreshBadgeDisplay() {
        _uiState.update { it.withBadgeCounts() }
    }

    fun markDeliveriesSectionOpened() {
        badgeStore.markHubDeliveriesSeen(currentDeliveryIds)
        _uiState.update { it.withBadgeCounts() }
    }

    fun markPurchaseSectionOpened() {
        badgeStore.markMainTabPurchaseSeen(purchaseContractIds)
        _uiState.update { it.withBadgeCounts() }
    }

    fun markSalesSectionOpened() {
        badgeStore.markMainTabSalesSeen(salesContractIds)
        _uiState.update { it.withBadgeCounts() }
    }

    fun markNotificationsSectionOpened() {
        val approvalKeysList = if (_uiState.value.showApprovalsBadge) approvalKeys else emptyList()
        badgeStore.markHubNotificationsSeen(
            notificationIds = notificationIds,
            approvalKeys = approvalKeysList,
        )
        _uiState.update { it.withBadgeCounts() }
    }

    /** Clears the bottom-nav badge for [tab] when the user opens that section. */
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
                    it.copy(pendingDeliveries = deliveries.size).withBadgeCounts()
                }
            }
        }
        viewModelScope.launch {
            purchaseContractsRepository.observeExpiring(accountId).collect { contracts ->
                purchaseContractIds = contracts.map { it.id }
                _uiState.update {
                    it.copy(purchaseExpiringCount = contracts.size).withBadgeCounts()
                }
            }
        }
        viewModelScope.launch {
            salesContractsRepository.observeExpiring(accountId).collect { contracts ->
                salesContractIds = contracts.map { it.id }
                _uiState.update {
                    it.copy(salesExpiringCount = contracts.size).withBadgeCounts()
                }
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
                    _uiState.update {
                        it.copy(
                            pendingApprovals = snapshot.approvals.size,
                            pendingDeliveries = snapshot.pendingDeliveries.size,
                            purchaseExpiringCount = snapshot.pendingPurchaseContracts.size,
                            salesExpiringCount = snapshot.pendingSalesContracts.size,
                        ).withBadgeCounts()
                    }
                } else {
                    purchaseContractIds = snapshot.pendingPurchaseContracts.map { it.id }
                    salesContractIds = snapshot.pendingSalesContracts.map { it.id }
                    _uiState.update {
                        it.copy(
                            purchaseExpiringCount = snapshot.pendingPurchaseContracts.size,
                            salesExpiringCount = snapshot.pendingSalesContracts.size,
                        ).withBadgeCounts()
                    }
                }
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
                    it.copy(pendingApprovals = cachedApprovals.size).withBadgeCounts()
                }
            }
            runCatching { deliveriesRepository.listCurrent(accountId) }
            runCatching { purchaseContractsRepository.load(accountId) }
            runCatching { salesContractsRepository.load(accountId) }
            onDataSynced()
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
            ).withBadgeCounts()
        }
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

    private fun HubUiState.withBadgeCounts(): HubUiState =
        copy(
            notificationBadgeCount = badgeStore.hubNotificationBadge(
                notificationIds,
                if (showApprovalsBadge) approvalKeys else emptyList(),
            ),
            hasUrgentNotifications = hasUrgentNotifications,
            deliveryBadgeCount = badgeStore.hubDeliveryBadge(currentDeliveryIds),
            purchaseBadgeCount = badgeStore.mainTabPurchaseBadge(purchaseContractIds),
            salesBadgeCount = badgeStore.mainTabSalesBadge(salesContractIds),
        )
}
