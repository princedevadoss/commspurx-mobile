package com.commspurx.mobile.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM data messages from the server and triggers a notification sync.
 */
class CommspurxFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        when (message.data["type"]) {
            "sync" -> NotificationPollManager.schedulePoll(
                context = applicationContext,
                resetBaseline = false,
                debounce = false,
            )
        }
    }
}
