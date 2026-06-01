package com.commspurx.mobile.notifications

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.model.canAccessApprovals
import com.commspurx.mobile.data.local.SessionStore
import com.commspurx.mobile.data.repository.ApprovalsRepository
import com.commspurx.mobile.data.repository.DeliveriesRepository
import com.commspurx.mobile.data.repository.NotificationsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotificationMonitorService : LifecycleService() {
    private lateinit var sessionStore: SessionStore
    private lateinit var notificationsRepository: NotificationsRepository
    private lateinit var approvalsRepository: ApprovalsRepository
    private lateinit var deliveriesRepository: DeliveriesRepository
    private lateinit var systemNotificationHelper: SystemNotificationHelper

    private var pollJob: Job? = null
    private var isAdmin = false
    private var baselineSet = false
    private val seenApprovalKeys = mutableSetOf<String>()
    private val seenNotificationIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val app = application as com.commspurx.mobile.CommspurxApplication
        sessionStore = app.sessionStore
        notificationsRepository = app.notificationsRepository
        approvalsRepository = app.approvalsRepository
        deliveriesRepository = app.deliveriesRepository
        systemNotificationHelper = SystemNotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        isAdmin = intent?.getBooleanExtra(EXTRA_IS_ADMIN, false) ?: false
        baselineSet = false
        seenApprovalKeys.clear()
        seenNotificationIds.clear()

        val notification = systemNotificationHelper.showMonitorForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                SystemNotificationHelper.MONITOR_FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(SystemNotificationHelper.MONITOR_FOREGROUND_ID, notification)
        }

        if (sessionStore.getRefreshToken() == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startPolling()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                pollOnce()
                val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                delay(if (inForeground) FOREGROUND_POLL_MS else BACKGROUND_POLL_MS)
            }
        }
    }

    private suspend fun pollOnce() {
        if (sessionStore.getRefreshToken() == null) {
            stopSelf()
            return
        }
        try {
            val accountId = sessionStore.getSessionSnapshot()?.user?.accountId ?: return
            val notifications = notificationsRepository.listUnread(accountId)
            val approvals = if (isAdmin) approvalsRepository.listPending() else emptyList()
            val pendingDeliveries = deliveriesRepository.listCurrent(accountId)
            val snapshot = MonitorSnapshot(
                approvals = approvals,
                notifications = notifications,
                pendingDeliveries = pendingDeliveries,
            )
            MonitorState.emitSnapshot(snapshot)

            if (!baselineSet) {
                seedBaseline(approvals, notifications)
                baselineSet = true
                return
            }

            for (item in approvals) {
                val key = approvalKey(item)
                if (key !in seenApprovalKeys) {
                    seenApprovalKeys.add(key)
                    systemNotificationHelper.showApprovalNotification(item)
                }
            }

            for (item in notifications) {
                if (item.id !in seenNotificationIds) {
                    seenNotificationIds.add(item.id)
                    systemNotificationHelper.showActivityNotification(item)
                }
            }
        } catch (_: Exception) {
            // Keep polling on transient errors
        }
    }

    private fun seedBaseline(
        approvals: List<ApprovalItem>,
        notifications: List<NotificationItem>,
    ) {
        seenApprovalKeys.clear()
        seenNotificationIds.clear()
        approvals.forEach { seenApprovalKeys.add(approvalKey(it)) }
        notifications.forEach { seenNotificationIds.add(it.id) }
    }

    private fun approvalKey(item: ApprovalItem): String =
        "${item.entityType}:${item.id}"

    companion object {
        private const val EXTRA_IS_ADMIN = "is_admin"
        private const val FOREGROUND_POLL_MS = 5_000L
        private const val BACKGROUND_POLL_MS = 15_000L

        fun start(context: Context, role: UserRole) {
            val intent = Intent(context, NotificationMonitorService::class.java).apply {
                putExtra(EXTRA_IS_ADMIN, role.canAccessApprovals())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotificationMonitorService::class.java))
        }
    }
}

object MonitorState {
    private val _snapshots = MutableSharedFlow<MonitorSnapshot>(extraBufferCapacity = 1)
    val snapshots: SharedFlow<MonitorSnapshot> = _snapshots.asSharedFlow()

    private val _pendingDeepLink = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingDeepLink: StateFlow<DeepLinkTarget?> = _pendingDeepLink.asStateFlow()

    fun emitSnapshot(snapshot: MonitorSnapshot) {
        _snapshots.tryEmit(snapshot)
    }

    fun setPendingDeepLink(target: DeepLinkTarget?) {
        _pendingDeepLink.value = target
    }

    fun consumePendingDeepLink(): DeepLinkTarget? {
        val current = _pendingDeepLink.value
        _pendingDeepLink.value = null
        return current
    }
}
