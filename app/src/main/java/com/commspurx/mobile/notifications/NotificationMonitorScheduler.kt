package com.commspurx.mobile.notifications

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.local.MonitorSeenStore
import java.util.concurrent.TimeUnit

object NotificationMonitorScheduler {
    private const val CHAIN_WORK_NAME = "commspurx_notification_poll_chain"
    private const val IMMEDIATE_WORK_NAME = "commspurx_notification_poll_immediate"
    private const val PERIODIC_WORK_NAME = "commspurx_notification_poll_periodic"

    /** Fast poll while [NotificationMonitorService] foreground loop is running. */
    const val FOREGROUND_POLL_INTERVAL_MS = 30_000L

    /** Background poll interval (WorkManager / AlarmManager). */
    const val BACKGROUND_POLL_INTERVAL_MS = 15 * 60 * 1000L

    /** @deprecated Use [FOREGROUND_POLL_INTERVAL_MS] or [BACKGROUND_POLL_INTERVAL_MS]. */
    const val POLL_INTERVAL_MS = BACKGROUND_POLL_INTERVAL_MS

    private val BACKGROUND_POLL_INTERVAL_MINUTES =
        TimeUnit.MILLISECONDS.toMinutes(BACKGROUND_POLL_INTERVAL_MS)

    fun ensureRunning(context: Context, resetBaseline: Boolean = false, fromForeground: Boolean = true) {
        if (fromForeground) {
            NotificationMonitorService.start(context, resetBaseline)
        }
        armBackgroundPolling(context)
    }

    fun armBackgroundPolling(context: Context) {
        scheduleChainedPoll(context)
        schedulePeriodicPoll(context)
        NotificationAlarmScheduler.scheduleNext(context)
    }

    fun enqueueBackgroundPoll(context: Context, expedited: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val builder = OneTimeWorkRequestBuilder<NotificationPollWorker>()
            .setConstraints(constraints)
        if (expedited && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
    }

    fun stop(context: Context) {
        NotificationMonitorService.stop(context)
        NotificationAlarmScheduler.cancel(context)
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(CHAIN_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelUniqueWork(PERIODIC_WORK_NAME)
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
            .setInitialDelay(BACKGROUND_POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun schedulePeriodicPoll(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(
            BACKGROUND_POLL_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
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
