package com.commspurx.mobile.notifications

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
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
    private lateinit var systemNotificationHelper: SystemNotificationHelper

    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        systemNotificationHelper = SystemNotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val resetBaseline = intent?.getBooleanExtra(EXTRA_RESET_BASELINE, false) ?: false

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

        val app = application as com.commspurx.mobile.CommspurxApplication
        if (app.sessionStore.getRefreshToken() == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationPollManager.schedulePoll(
            context = applicationContext,
            resetBaseline = resetBaseline,
            debounce = !resetBaseline,
        )

        if (pollJob?.isActive != true) {
            startPollingLoop()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val app = application as com.commspurx.mobile.CommspurxApplication
        if (app.sessionStore.getRefreshToken() == null) return

        NotificationMonitorScheduler.armBackgroundPolling(applicationContext)
        NotificationMonitorScheduler.enqueueBackgroundPoll(applicationContext)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val result = NotificationPollManager.runPollNow(applicationContext, resetBaseline = false)
                if (result == PollResult.NoSession) {
                    stopSelf()
                    return@launch
                }
            }
        }
    }

    companion object {
        private const val EXTRA_RESET_BASELINE = "reset_baseline"
        private val POLL_INTERVAL_MS = NotificationMonitorScheduler.POLL_INTERVAL_MS

        fun start(context: Context, resetBaseline: Boolean = false) {
            val intent = Intent(context, NotificationMonitorService::class.java).apply {
                putExtra(EXTRA_RESET_BASELINE, resetBaseline)
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
