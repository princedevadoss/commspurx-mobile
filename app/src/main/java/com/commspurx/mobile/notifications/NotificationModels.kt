package com.commspurx.mobile.notifications

sealed class DeepLinkTarget {
    data object Approvals : DeepLinkTarget()

    data object Activity : DeepLinkTarget()

    data object Deliveries : DeepLinkTarget()

    data class BulkImport(
        val jobId: String,
        val notificationId: String? = null,
    ) : DeepLinkTarget()

    data class ActivityItem(
        val notificationId: String,
    ) : DeepLinkTarget()
}

data class MonitorSnapshot(
    val approvals: List<com.commspurx.mobile.data.model.ApprovalItem>,
    val notifications: List<com.commspurx.mobile.data.model.NotificationItem>,
    val pendingDeliveries: List<com.commspurx.mobile.data.model.DeliveryItem> = emptyList(),
)

object NotificationExtras {
    const val DESTINATION = "destination"
    const val NOTIFICATION_ID = "notification_id"
    const val BULK_JOB_ID = "bulk_job_id"

    const val DEST_APPROVALS = "approvals"
    const val DEST_ACTIVITY = "activity"
    const val DEST_BULK_IMPORT = "bulk_import"
    const val DEST_DELIVERIES = "deliveries"
}
