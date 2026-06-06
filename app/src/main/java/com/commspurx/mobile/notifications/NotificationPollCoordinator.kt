package com.commspurx.mobile.notifications

import android.content.Context
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.local.BadgeCounts
import com.commspurx.mobile.data.local.MonitorSeenState
import com.commspurx.mobile.data.local.MonitorSeenStore
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.model.canAccessApprovals
import com.commspurx.mobile.data.model.isCompleted
import kotlinx.coroutines.flow.first

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
        val canAccessApprovals =
            UserRole.from(session.user.role)?.canAccessApprovals() == true

        if (resetBaseline) {
            seenStore.clear()
        }

        var seen = seenStore.load()

        val fetch = fetchSnapshot(accountId, canAccessApprovals)

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
                purchaseTotal = fetch.purchaseTotal,
                salesTotal = fetch.salesTotal,
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
        seen = notifyNewExpiringContracts(
            seen,
            fetch.purchaseContracts,
            fetch.salesContracts,
            fetch.purchaseTotal,
            fetch.salesTotal,
        )
        seenStore.save(seen)
        return PollResult.Notified
    }

    private suspend fun fetchSnapshot(
        accountId: String,
        canAccessApprovals: Boolean,
    ): FetchedSnapshot {
        val networkAvailable = app.connectivityMonitor.hasNetworkNow()
        var syncAttempted = false
        var anySyncOk = false

        if (networkAvailable) {
            syncAttempted = true
            anySyncOk = runCatching { app.notificationsRepository.refresh(accountId) }.getOrDefault(false) || anySyncOk
            anySyncOk = runCatching { app.deliveriesRepository.refresh(accountId) }.getOrDefault(false) || anySyncOk
            if (canAccessApprovals) {
                anySyncOk = runCatching { app.approvalsRepository.refreshPending() }.getOrDefault(false) || anySyncOk
            }
        }

        val notifications = runCatching { app.notificationsRepository.listUnread(accountId) }
            .getOrDefault(emptyList())
        val approvals = if (canAccessApprovals) {
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
        runCatching { app.purchaseContractsRepository.load(accountId) }
        runCatching { app.salesContractsRepository.load(accountId) }
        val purchaseContracts = app.purchaseContractsRepository.observeExpiring(accountId).first()
        val salesContracts = app.salesContractsRepository.observeExpiring(accountId).first()
        val purchaseTotal = app.purchaseContractsRepository.totalExpiring.value
        val salesTotal = app.salesContractsRepository.totalExpiring.value

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
            purchaseTotal = purchaseTotal,
            salesTotal = salesTotal,
        )
    }

    private fun seedBaseline(
        approvals: List<ApprovalItem>,
        notifications: List<NotificationItem>,
        pendingDeliveries: List<DeliveryItem>,
        completedDeliveries: List<DeliveryItem>,
        purchaseTotal: Int,
        salesTotal: Int,
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
            purchaseContractIds = if (purchaseTotal > BadgeCounts.DISPLAY_CAP) {
                emptySet()
            } else {
                purchaseContracts.map { it.id }.toSet()
            },
            salesContractIds = if (salesTotal > BadgeCounts.DISPLAY_CAP) {
                emptySet()
            } else {
                salesContracts.map { it.id }.toSet()
            },
            purchaseExpiringSeenCount = purchaseTotal,
            salesExpiringSeenCount = salesTotal,
        )
    }

    private fun notifyNewApprovals(
        seen: MonitorSeenState,
        approvals: List<ApprovalItem>,
    ): MonitorSeenState {
        val newItems = approvals.filter { approvalKey(it) !in seen.approvalKeys }
        if (newItems.isEmpty()) return seen
        val newKeys = newItems.map { approvalKey(it) }.toSet()
        if (newItems.size > BadgeCounts.DISPLAY_CAP) {
            systemNotificationHelper.showApprovalsSummary(newItems.size)
        } else {
            for (item in newItems) {
                systemNotificationHelper.showApprovalNotification(item)
            }
        }
        return seen.copy(approvalKeys = seen.approvalKeys + newKeys)
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
        val newDeliveries = deliveries.filter { it.id !in seen.deliveryIds }
        if (newDeliveries.isEmpty()) return seen
        val newIds = newDeliveries.map { it.id }.toSet()
        if (newDeliveries.size > BadgeCounts.DISPLAY_CAP) {
            systemNotificationHelper.showDeliveriesSummary(newDeliveries.size)
        } else {
            for (delivery in newDeliveries) {
                systemNotificationHelper.showPendingDeliveryNotification(delivery)
            }
        }
        return seen.copy(deliveryIds = seen.deliveryIds + newIds)
    }

    private fun notifyNewExpiringContracts(
        seen: MonitorSeenState,
        purchaseContracts: List<ContractSummary>,
        salesContracts: List<ContractSummary>,
        purchaseTotal: Int,
        salesTotal: Int,
    ): MonitorSeenState {
        var next = seen
        val purchaseNew = BadgeCounts.unseen(purchaseTotal, seen.purchaseExpiringSeenCount)
        if (purchaseNew > 0) {
            if (purchaseTotal > BadgeCounts.DISPLAY_CAP) {
                systemNotificationHelper.showPurchaseExpiringSummary(purchaseNew)
                next = next.copy(
                    purchaseExpiringSeenCount = purchaseTotal,
                    purchaseContractIds = emptySet(),
                )
            } else {
                var purchaseContractIds = seen.purchaseContractIds
                for (contract in purchaseContracts) {
                    if (contract.id !in purchaseContractIds) {
                        purchaseContractIds = purchaseContractIds + contract.id
                        systemNotificationHelper.showPendingPurchaseContractNotification(contract)
                    }
                }
                next = next.copy(
                    purchaseContractIds = purchaseContractIds,
                    purchaseExpiringSeenCount = purchaseTotal,
                )
            }
        }
        val salesNew = BadgeCounts.unseen(salesTotal, next.salesExpiringSeenCount)
        if (salesNew > 0) {
            if (salesTotal > BadgeCounts.DISPLAY_CAP) {
                systemNotificationHelper.showSalesExpiringSummary(salesNew)
                next = next.copy(
                    salesExpiringSeenCount = salesTotal,
                    salesContractIds = emptySet(),
                )
            } else {
                var salesContractIds = next.salesContractIds
                for (contract in salesContracts) {
                    if (contract.id !in salesContractIds) {
                        salesContractIds = salesContractIds + contract.id
                        systemNotificationHelper.showPendingSalesContractNotification(contract)
                    }
                }
                next = next.copy(
                    salesContractIds = salesContractIds,
                    salesExpiringSeenCount = salesTotal,
                )
            }
        }
        return next
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
        val purchaseTotal: Int,
        val salesTotal: Int,
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
