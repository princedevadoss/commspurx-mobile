package com.commspurx.mobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.commspurx.mobile.CommspurxApplication

/** Restarts background monitoring after reboot or app update when a session exists. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext as CommspurxApplication
        if (app.sessionStore.getSessionSnapshot() == null) return
        NotificationMonitorScheduler.armBackgroundPolling(app)
        NotificationMonitorScheduler.enqueueBackgroundPoll(app)
    }
}
