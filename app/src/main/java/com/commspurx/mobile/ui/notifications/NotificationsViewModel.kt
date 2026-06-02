package com.commspurx.mobile.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.local.MonitorSeenStore
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.BulkImportJobResult
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.model.canAccessApprovals
import com.commspurx.mobile.data.repository.ApprovalsRepository
import com.commspurx.mobile.data.repository.BulkImportRepository
import com.commspurx.mobile.data.repository.NotificationsRepository
import com.commspurx.mobile.notifications.DeepLinkTarget
import com.commspurx.mobile.notifications.MonitorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val notifications: List<NotificationItem> = emptyList(),
    val approvals: List<ApprovalItem> = emptyList(),
    val selectedTab: Int = 0,
    val approvalsTabBadge: Int = 0,
    val activityTabBadge: Int = 0,
    val isRefreshing: Boolean = false,
    val isLoadingJob: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val actingApprovalKey: String? = null,
    val selectedJob: BulkImportJobResult? = null,
    val showBulkImportSheet: Boolean = false,
    val isMarkingAllSeen: Boolean = false,
) {
    val role: UserRole? = UserRole.from(user.role)
    val showApprovalsTab: Boolean = role?.canAccessApprovals() == true
}

class NotificationsViewModel(
    user: AuthUser,
    account: AuthAccount?,
    private val notificationsRepository: NotificationsRepository,
    private val approvalsRepository: ApprovalsRepository,
    private val bulkImportRepository: BulkImportRepository,
    private val badgeStore: BadgeStore,
    private val monitorSeenStore: MonitorSeenStore,
    private val onNavigateToDeliveries: () -> Unit,
) : ViewModel() {
    private val accountId = user.accountId
    private val _uiState = MutableStateFlow(NotificationsUiState(user = user, account = account))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        observeCachedNotifications()
        observeMonitorSnapshots()
        observeDeepLinks()
        syncInBackground()
    }

    fun markHubNotificationsSeen() {
        val state = _uiState.value
        val approvalKeysList = state.approvals.map { BadgeStore.approvalKey(it.entityType, it.id) }
        val notificationIdsList = state.notifications.map { it.id }
        badgeStore.markHubNotificationsSeen(
            notificationIds = notificationIdsList,
            approvalKeys = approvalKeysList,
        )
        viewModelScope.launch {
            monitorSeenStore.markNotificationsSeen(notificationIdsList)
            monitorSeenStore.markApprovalsSeen(approvalKeysList)
        }
        markSelectedTabSeen()
    }

    fun refresh() {
        syncInBackground()
    }

    private fun observeCachedNotifications() {
        viewModelScope.launch {
            notificationsRepository.observeUnread(accountId).collect { notifications ->
                _uiState.update { it.copy(notifications = notifications, errorMessage = null) }
                refreshTabBadges()
            }
        }
    }

    private fun syncInBackground() {
        viewModelScope.launch {
            runCatching { notificationsRepository.listUnread(accountId) }
            if (_uiState.value.showApprovalsTab) {
                val approvals = approvalsRepository.listPending()
                _uiState.update { it.copy(approvals = approvals, errorMessage = null) }
                refreshTabBadges()
            }
        }
    }

    fun onNotificationClick(item: NotificationItem) {
        viewModelScope.launch {
            try {
                notificationsRepository.markRead(accountId, item.id)
                monitorSeenStore.markNotificationsSeen(listOf(item.id))
                if (item.entityType == "delivery" && item.title.contains("completed", ignoreCase = true)) {
                    monitorSeenStore.markCompletedDeliveriesSeen(listOf(item.entityId))
                }
                _uiState.update { state ->
                    state.copy(
                        notifications = state.notifications.filter { it.id != item.id },
                    )
                }
                refreshTabBadges()
                when (item.entityType) {
                    "bulk_import" -> loadBulkImportJob(item.entityId)
                    "delivery" -> onNavigateToDeliveries()
                    else -> Unit
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Could not update notification") }
            }
        }
    }

    fun dismissBulkImportSheet() {
        _uiState.update { it.copy(showBulkImportSheet = false, selectedJob = null) }
    }

    fun markAllSeen() {
        val notifications = _uiState.value.notifications
        if (notifications.isEmpty() || _uiState.value.isMarkingAllSeen) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMarkingAllSeen = true, errorMessage = null) }
            try {
                notificationsRepository.markAllRead(accountId)
                val ids = notifications.map { it.id }
                badgeStore.markActivityTabSeen(ids)
                monitorSeenStore.markNotificationsSeen(ids)
                _uiState.update {
                    it.copy(
                        notifications = emptyList(),
                        isMarkingAllSeen = false,
                        actionMessage = "All notifications marked as seen",
                    )
                }
                refreshTabBadges()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isMarkingAllSeen = false,
                        errorMessage = "Could not mark notifications as seen",
                    )
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        markSelectedTabSeen()
    }

    fun decideApproval(item: ApprovalItem, action: String) {
        val key = BadgeStore.approvalKey(item.entityType, item.id)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    actingApprovalKey = key,
                    errorMessage = null,
                    actionMessage = null,
                )
            }
            try {
                approvalsRepository.decide(item.entityType, item.id, action)
                monitorSeenStore.markApprovalsSeen(
                    listOf(BadgeStore.approvalKey(item.entityType, item.id)),
                )
                _uiState.update { state ->
                    state.copy(
                        approvals = state.approvals.filterNot {
                            it.entityType == item.entityType && it.id == item.id
                        },
                        actingApprovalKey = null,
                        actionMessage = if (action == "approve") {
                            "Entry approved"
                        } else {
                            "Entry rejected; submitter will be notified"
                        },
                    )
                }
                refreshTabBadges()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        actingApprovalKey = null,
                        errorMessage = "Could not update approval",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    private fun observeMonitorSnapshots() {
        viewModelScope.launch {
            MonitorState.snapshots.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        notifications = snapshot.notifications,
                        approvals = if (it.showApprovalsTab) snapshot.approvals else it.approvals,
                    )
                }
                refreshTabBadges()
            }
        }
    }

    private fun observeDeepLinks() {
        viewModelScope.launch {
            MonitorState.consumePendingDeepLink()?.let { applyDeepLink(it) }
            MonitorState.pendingDeepLink.filterNotNull().collect { target ->
                applyDeepLink(target)
                MonitorState.setPendingDeepLink(null)
            }
        }
    }

    private fun applyDeepLink(target: DeepLinkTarget) {
        when (target) {
            DeepLinkTarget.Approvals -> {
                _uiState.update { it.copy(selectedTab = 0) }
                markSelectedTabSeen()
            }
            DeepLinkTarget.Activity -> {
                _uiState.update {
                    it.copy(selectedTab = if (it.showApprovalsTab) 1 else 0)
                }
                markSelectedTabSeen()
            }
            DeepLinkTarget.Deliveries -> onNavigateToDeliveries()
            DeepLinkTarget.PurchaseContracts,
            DeepLinkTarget.SalesContracts,
            -> Unit // Main shell switches tabs via MonitorState / AppNavigation
            is DeepLinkTarget.ActivityItem -> {
                _uiState.update {
                    it.copy(selectedTab = if (it.showApprovalsTab) 1 else 0)
                }
                markSelectedTabSeen()
                val item = _uiState.value.notifications.find { it.id == target.notificationId }
                if (item != null) {
                    onNotificationClick(item)
                } else {
                    viewModelScope.launch {
                        refresh()
                        _uiState.value.notifications
                            .find { it.id == target.notificationId }
                            ?.let { onNotificationClick(it) }
                    }
                }
            }
            is DeepLinkTarget.BulkImport -> {
                _uiState.update {
                    it.copy(selectedTab = if (it.showApprovalsTab) 1 else 0)
                }
                markSelectedTabSeen()
                viewModelScope.launch {
                    target.notificationId?.let { id ->
                        runCatching { notificationsRepository.markRead(accountId, id) }
                        _uiState.update { state ->
                            state.copy(notifications = state.notifications.filter { it.id != id })
                        }
                    }
                    loadBulkImportJob(target.jobId)
                    refreshTabBadges()
                }
            }
        }
    }

    private fun loadBulkImportJob(jobId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingJob = true, showBulkImportSheet = true, selectedJob = null)
            }
            try {
                val job = bulkImportRepository.getJob(jobId)
                _uiState.update { it.copy(selectedJob = job, isLoadingJob = false) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingJob = false,
                        showBulkImportSheet = false,
                        errorMessage = "Could not load import results",
                    )
                }
            }
        }
    }

    private fun markSelectedTabSeen() {
        val state = _uiState.value
        if (state.showApprovalsTab) {
            when (state.selectedTab) {
                0 -> {
                    val keys = state.approvals.map { BadgeStore.approvalKey(it.entityType, it.id) }
                    badgeStore.markApprovalsTabSeen(keys)
                    viewModelScope.launch {
                        monitorSeenStore.markApprovalsSeen(keys)
                    }
                }
                else -> {
                    val ids = state.notifications.map { it.id }
                    badgeStore.markActivityTabSeen(ids)
                    viewModelScope.launch {
                        monitorSeenStore.markNotificationsSeen(ids)
                    }
                }
            }
        } else {
            val ids = state.notifications.map { it.id }
            badgeStore.markActivityTabSeen(ids)
            viewModelScope.launch {
                monitorSeenStore.markNotificationsSeen(ids)
            }
        }
        refreshTabBadges()
    }

    private fun refreshTabBadges() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                approvalsTabBadge = if (state.showApprovalsTab) {
                    badgeStore.approvalsTabBadge(
                        state.approvals.map { item -> BadgeStore.approvalKey(item.entityType, item.id) },
                    )
                } else {
                    0
                },
                activityTabBadge = badgeStore.activityTabBadge(state.notifications.map { n -> n.id }),
            )
        }
    }
}
