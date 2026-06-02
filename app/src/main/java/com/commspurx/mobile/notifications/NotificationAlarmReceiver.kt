package com.commspurx.mobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** AlarmManager entry point — runs a serialized poll even when the app process was killed. */
class NotificationAlarmReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != NotificationAlarmScheduler.ACTION_POLL) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                NotificationPollManager.runPollNow(context.applicationContext, resetBaseline = false)
                NotificationMonitorScheduler.scheduleChainedPoll(context.applicationContext)
                NotificationAlarmScheduler.scheduleNext(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
