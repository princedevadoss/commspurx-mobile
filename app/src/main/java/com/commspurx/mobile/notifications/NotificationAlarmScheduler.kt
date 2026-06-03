package com.commspurx.mobile.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.commspurx.mobile.CommspurxApplication

/** Wakes the app process to poll even when the foreground service was killed. */
object NotificationAlarmScheduler {
    private const val REQUEST_CODE = 84001
    const val ACTION_POLL = "com.commspurx.mobile.action.NOTIFICATION_POLL"

    fun scheduleNext(context: Context) {
        val app = context.applicationContext as CommspurxApplication
        if (app.sessionStore.getRefreshToken() == null) return

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + NotificationMonitorScheduler.BACKGROUND_POLL_INTERVAL_MS
        val pendingIntent = pollPendingIntent(app)

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent,
        )
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pollPendingIntent(app))
    }

    internal fun pollPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_POLL
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
