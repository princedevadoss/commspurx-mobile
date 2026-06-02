package com.commspurx.mobile.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.local.MonitorSeenStore
import java.util.concurrent.TimeUnit

object NotificationMonitorScheduler {
    private const val CHAIN_WORK_NAME = "commspurx_notification_poll_chain"
    private const val IMMEDIATE_WORK_NAME = "commspurx_notification_poll_immediate"

    /** Background poll interval (WorkManager minimum practical cadence). */
    const val POLL_INTERVAL_MS = 15 * 60 * 1000L
    private val POLL_INTERVAL_MINUTES = TimeUnit.MILLISECONDS.toMinutes(POLL_INTERVAL_MS)

    fun ensureRunning(context: Context, resetBaseline: Boolean = false, fromForeground: Boolean = true) {
        if (fromForeground) {
            NotificationMonitorService.start(context, resetBaseline)
        }
        armBackgroundPolling(context)
    }

    fun armBackgroundPolling(context: Context) {
        scheduleChainedPoll(context)
        NotificationAlarmScheduler.scheduleNext(context)
    }

    fun enqueueBackgroundPoll(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NotificationPollWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun stop(context: Context) {
        NotificationMonitorService.stop(context)
        NotificationAlarmScheduler.cancel(context)
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(CHAIN_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
        kotlinx.coroutines.runBlocking {
            MonitorSeenStore(context).clear()
        }
    }

    internal fun scheduleChainedPoll(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NotificationPollWorker>()
            .setInitialDelay(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class NotificationPollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CommspurxApplication
        if (app.sessionStore.getRefreshToken() == null) {
            return Result.success()
        }

        NotificationPollManager.runPollNow(applicationContext, resetBaseline = false)
        NotificationMonitorScheduler.scheduleChainedPoll(applicationContext)
        NotificationAlarmScheduler.scheduleNext(applicationContext)

        return Result.success()
    }
}
