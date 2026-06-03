package com.commspurx.mobile.notifications

import android.content.Context
import android.os.Build
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.model.FcmTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Registers the device FCM token with the API after login / on token refresh.
 */
object FcmTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val PREFS = "commspurx_fcm_prefs"
    private const val KEY_LAST_REGISTERED = "last_registered_token"

    fun isFirebaseConfigured(context: Context): Boolean {
        return runCatching {
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
    }

    fun syncTokenIfPossible(context: Context) {
        val appContext = context.applicationContext
        if (!isFirebaseConfigured(appContext)) return
        val app = appContext as? CommspurxApplication ?: return
        if (app.sessionStore.getRefreshToken() == null) return

        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                registerWithBackend(app, token)
            }
        }
    }

    fun registerToken(context: Context, token: String) {
        val app = context.applicationContext as? CommspurxApplication ?: return
        if (app.sessionStore.getRefreshToken() == null) return
        scope.launch {
            runCatching { registerWithBackend(app, token) }
        }
    }

    fun unregisterCurrentToken(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? CommspurxApplication ?: return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(KEY_LAST_REGISTERED, null) ?: return

        scope.launch {
            runCatching {
                app.syncAuthTokenFromStore()
                app.apiClient.mobilePushApi.unregisterFcmToken(
                    body = FcmTokenRequest(token = lastToken),
                )
            }
            prefs.edit().remove(KEY_LAST_REGISTERED).apply()
        }
    }

    private suspend fun registerWithBackend(app: CommspurxApplication, token: String) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_REGISTERED, null) == token) return

        app.syncAuthTokenFromStore()
        app.apiClient.mobilePushApi.registerFcmToken(
            body = FcmTokenRequest(
                token = token,
                deviceLabel = deviceLabel(),
            ),
        )
        prefs.edit().putString(KEY_LAST_REGISTERED, token).apply()
    }

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manufacturer, model)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { "Android" }
    }
}
