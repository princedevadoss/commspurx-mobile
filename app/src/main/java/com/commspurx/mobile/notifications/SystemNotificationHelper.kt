package com.commspurx.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.commspurx.mobile.MainActivity
import com.commspurx.mobile.R
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.APPROVAL_ENTITY_LABELS

class SystemNotificationHelper(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    fun showApprovalNotification(item: ApprovalItem) {
        val entityLabel = APPROVAL_ENTITY_LABELS[item.entityType] ?: item.entityType
        val notificationId = approvalNotificationId(item)
        val intent = deepLinkIntent(
            destination = NotificationExtras.DEST_APPROVALS,
        )
        post(
            id = notificationId,
            title = "Approval required",
            body = "$entityLabel: ${item.label}",
            intent = intent,
            channelId = CHANNEL_ALERTS,
        )
    }

    fun showActivityNotification(item: NotificationItem) {
        val notificationId = activityNotificationId(item.id)
        val destination = when (item.entityType) {
            "bulk_import" -> NotificationExtras.DEST_BULK_IMPORT
            "delivery" -> NotificationExtras.DEST_DELIVERIES
            else -> NotificationExtras.DEST_ACTIVITY
        }
        val intent = deepLinkIntent(
            destination = destination,
            notificationId = item.id,
            bulkJobId = if (item.entityType == "bulk_import") item.entityId else null,
        )
        if (item.isHighPriority) {
            postUrgent(
                id = notificationId,
                title = item.title,
                body = item.message,
                intent = intent,
            )
        } else {
            post(
                id = notificationId,
                title = item.title,
                body = item.message,
                intent = intent,
                channelId = CHANNEL_ALERTS,
            )
        }
    }

    fun showMonitorForegroundNotification(): android.app.Notification {
        createMonitorChannel()
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags(),
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.monitor_notification_title))
            .setContentText(context.getString(R.string.monitor_notification_body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun post(
        id: Int,
        title: String,
        body: String,
        intent: Intent,
        channelId: String,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            pendingIntentFlags(),
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun postUrgent(
        id: Int,
        title: String,
        body: String,
        intent: Intent,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            pendingIntentFlags(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 280, 140, 280))
            .build()
        notificationManager.notify(id, notification)
    }

    private fun deepLinkIntent(
        destination: String,
        notificationId: String? = null,
        bulkJobId: String? = null,
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationExtras.DESTINATION, destination)
            notificationId?.let { putExtra(NotificationExtras.NOTIFICATION_ID, it) }
            bulkJobId?.let { putExtra(NotificationExtras.BULK_JOB_ID, it) }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_alerts_desc)
        }
        manager.createNotificationChannel(alerts)
        val urgent = NotificationChannel(
            CHANNEL_URGENT,
            context.getString(R.string.notification_channel_urgent),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_urgent_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 280, 140, 280)
            enableLights(true)
        }
        manager.createNotificationChannel(urgent)
        createMonitorChannel()
    }

    private fun createMonitorChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.notification_channel_monitor),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_monitor_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(monitor)
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        const val CHANNEL_ALERTS = "commspurx_alerts"
        const val CHANNEL_URGENT = "commspurx_urgent"
        const val CHANNEL_MONITOR = "commspurx_monitor"
        const val MONITOR_FOREGROUND_ID = 9001

        fun approvalNotificationId(item: ApprovalItem): Int =
            "approval:${item.entityType}:${item.id}".hashCode()

        fun activityNotificationId(notificationId: String): Int =
            "activity:$notificationId".hashCode()
    }
}

fun deepLinkFromIntent(intent: Intent?): DeepLinkTarget? {
    if (intent == null) return null
    return when (intent.getStringExtra(NotificationExtras.DESTINATION)) {
        NotificationExtras.DEST_APPROVALS -> DeepLinkTarget.Approvals
        NotificationExtras.DEST_ACTIVITY -> {
            val id = intent.getStringExtra(NotificationExtras.NOTIFICATION_ID)
            if (id != null) DeepLinkTarget.ActivityItem(id) else DeepLinkTarget.Activity
        }
        NotificationExtras.DEST_BULK_IMPORT -> {
            val jobId = intent.getStringExtra(NotificationExtras.BULK_JOB_ID) ?: return null
            DeepLinkTarget.BulkImport(
                jobId = jobId,
                notificationId = intent.getStringExtra(NotificationExtras.NOTIFICATION_ID),
            )
        }
        NotificationExtras.DEST_DELIVERIES -> DeepLinkTarget.Deliveries
        else -> null
    }
}
