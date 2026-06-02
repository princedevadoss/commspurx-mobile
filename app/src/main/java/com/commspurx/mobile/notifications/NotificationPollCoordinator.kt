package com.commspurx.mobile.notifications

import android.content.Context
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.local.MonitorSeenState
import com.commspurx.mobile.data.local.MonitorSeenStore
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.model.canAccessApprovals
import com.commspurx.mobile.data.model.isCompleted

class NotificationPollCoordinator(context: Context) {
    private val app = context.applicationContext as CommspurxApplication
    private val seenStore = app.monitorSeenStore
    private val systemNotificationHelper = SystemNotificationHelper(context.applicationContext)

    suspend fun pollAndNotify(resetBaseline: Boolean = false): PollResult {
        if (app.sessionStore.getRefreshToken() == null) {
            return PollResult.NoSession
        }

        val session = app.sessionStore.getSessionSnapshot() ?: return PollResult.NoSession
        val accountId = session.user.accountId
        val isAdmin = UserRole.from(session.user.role)?.canAccessApprovals() == true

        if (resetBaseline) {
            seenStore.clear()
        }

        var seen = seenStore.load()

        val fetch = fetchSnapshot(accountId, isAdmin)

        MonitorState.emitSnapshot(
            MonitorSnapshot(
                approvals = fetch.approvals,
                notifications = fetch.notifications,
                pendingDeliveries = fetch.pendingDeliveries,
                pendingPurchaseContracts = fetch.purchaseContracts,
                pendingSalesContracts = fetch.salesContracts,
            ),
        )

        if (!seen.baselineSet) {
            if (!fetch.networkAvailable) {
                return PollResult.WaitingForData
            }
            if (!fetch.syncAttempted) {
                return PollResult.WaitingForData
            }
            seen = seedBaseline(
                approvals = fetch.approvals,
                notifications = fetch.notifications,
                pendingDeliveries = fetch.pendingDeliveries,
                completedDeliveries = fetch.completedDeliveries,
                purchaseContracts = fetch.purchaseContracts,
                salesContracts = fetch.salesContracts,
            )
            seenStore.save(seen)
            return PollResult.BaselineSeeded
        }

        if (!systemNotificationHelper.canPostNotifications()) {
            return PollResult.PermissionRequired
        }

        seen = notifyNewApprovals(seen, fetch.approvals)
        seen = notifyNewActivityItems(seen, fetch.notifications)
        seen = notifyNewPendingDeliveries(seen, fetch.pendingDeliveries)
        seenStore.save(seen)
        return PollResult.Notified
    }

    private suspend fun fetchSnapshot(accountId: String, isAdmin: Boolean): FetchedSnapshot {
        val networkAvailable = app.connectivityMonitor.hasNetwork.value
        var syncAttempted = false
        var anySyncOk = false

        if (networkAvailable) {
            syncAttempted = true
            anySyncOk = runCatching { app.notificationsRepository.refresh(accountId) }.getOrDefault(false) || anySyncOk
            anySyncOk = runCatching { app.deliveriesRepository.refresh(accountId) }.getOrDefault(false) || anySyncOk
            if (isAdmin) {
                anySyncOk = runCatching { app.approvalsRepository.refreshPending() }.getOrDefault(false) || anySyncOk
            }
            runCatching { app.purchaseContractsRepository.refresh(accountId) }
            runCatching { app.salesContractsRepository.refresh(accountId) }
        }

        val notifications = runCatching { app.notificationsRepository.listUnread(accountId) }
            .getOrDefault(emptyList())
        val approvals = if (isAdmin) {
            runCatching { app.approvalsRepository.listPending() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val pendingDeliveries = runCatching { app.deliveriesRepository.listCurrent(accountId) }
            .getOrDefault(emptyList())
            .filter { !it.isCompleted() }
        val completedDeliveries = runCatching { app.deliveriesRepository.listCompleted(accountId) }
            .getOrDefault(emptyList())
            .filter { it.isCompleted() }
        val purchaseContracts = runCatching { app.purchaseContractsRepository.load(accountId) }
            .getOrDefault(emptyList())
        val salesContracts = runCatching { app.salesContractsRepository.load(accountId) }
            .getOrDefault(emptyList())

        return FetchedSnapshot(
            networkAvailable = networkAvailable,
            syncAttempted = syncAttempted,
            syncSucceeded = anySyncOk,
            notifications = notifications,
            approvals = approvals,
            pendingDeliveries = pendingDeliveries,
            completedDeliveries = completedDeliveries,
            purchaseContracts = purchaseContracts,
            salesContracts = salesContracts,
        )
    }

    private fun seedBaseline(
        approvals: List<ApprovalItem>,
        notifications: List<NotificationItem>,
        pendingDeliveries: List<DeliveryItem>,
        completedDeliveries: List<DeliveryItem>,
        purchaseContracts: List<ContractSummary>,
        salesContracts: List<ContractSummary>,
    ): MonitorSeenState {
        return MonitorSeenState(
            baselineSet = true,
            approvalKeys = approvals.map { approvalKey(it) }.toSet(),
            notificationIds = notifications
                .filter { shouldNotifyActivityOs(it) }
                .map { it.id }
                .toSet(),
            deliveryIds = pendingDeliveries.map { it.id }.toSet(),
            completedDeliveryIds = completedDeliveries.map { it.id }.toSet(),
            purchaseContractIds = purchaseContracts.map { it.id }.toSet(),
            salesContractIds = salesContracts.map { it.id }.toSet(),
        )
    }

    private fun notifyNewApprovals(
        seen: MonitorSeenState,
        approvals: List<ApprovalItem>,
    ): MonitorSeenState {
        var approvalKeys = seen.approvalKeys
        for (item in approvals) {
            val key = approvalKey(item)
            if (key !in approvalKeys) {
                approvalKeys = approvalKeys + key
                systemNotificationHelper.showApprovalNotification(item)
            }
        }
        return seen.copy(approvalKeys = approvalKeys)
    }

    private fun notifyNewActivityItems(
        seen: MonitorSeenState,
        notifications: List<NotificationItem>,
    ): MonitorSeenState {
        var notificationIds = seen.notificationIds
        var completedDeliveryIds = seen.completedDeliveryIds
        for (item in notifications) {
            if (!shouldNotifyActivityOs(item)) continue
            if (item.id !in notificationIds) {
                notificationIds = notificationIds + item.id
                systemNotificationHelper.showActivityNotification(item)
                if (item.entityType == "delivery" && item.title.contains("completed", ignoreCase = true)) {
                    completedDeliveryIds = completedDeliveryIds + item.entityId
                }
            }
        }
        return seen.copy(
            notificationIds = notificationIds,
            completedDeliveryIds = completedDeliveryIds,
        )
    }

    private fun notifyNewPendingDeliveries(
        seen: MonitorSeenState,
        deliveries: List<DeliveryItem>,
    ): MonitorSeenState {
        var deliveryIds = seen.deliveryIds
        for (delivery in deliveries) {
            if (delivery.id !in deliveryIds) {
                deliveryIds = deliveryIds + delivery.id
                systemNotificationHelper.showPendingDeliveryNotification(delivery)
            }
        }
        return seen.copy(deliveryIds = deliveryIds)
    }

    private fun approvalKey(item: ApprovalItem): String =
        MonitorSeenStore.approvalKey(item.entityType, item.id)

    companion object {
        fun shouldNotifyActivityOs(item: NotificationItem): Boolean {
            return when (item.entityType) {
                "purchase_contract",
                "sales_contract",
                -> false
                "delivery" -> item.title.contains("completed", ignoreCase = true)
                "location",
                "oil_product",
                "broker",
                "owner",
                "bulk_import",
                -> true
                else -> true
            }
        }
    }

    private data class FetchedSnapshot(
        val networkAvailable: Boolean,
        val syncAttempted: Boolean,
        val syncSucceeded: Boolean,
        val notifications: List<NotificationItem>,
        val approvals: List<ApprovalItem>,
        val pendingDeliveries: List<DeliveryItem>,
        val completedDeliveries: List<DeliveryItem>,
        val purchaseContracts: List<ContractSummary>,
        val salesContracts: List<ContractSummary>,
    )
}

enum class PollResult {
    NoSession,
    WaitingForData,
    BaselineSeeded,
    PermissionRequired,
    Notified,
    Error,
}
