package com.commspurx.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.commspurx.mobile.notifications.BatteryOptimizationHelper
import com.commspurx.mobile.notifications.MonitorState
import com.commspurx.mobile.notifications.deepLinkFromIntent
import com.commspurx.mobile.ui.navigation.AppNavigation
import com.commspurx.mobile.ui.theme.CommspurxTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ensureNotificationMonitorRunning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val darkTheme = isSystemInDarkTheme()

            CommspurxTheme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasNotificationPermission()) {
            ensureNotificationMonitorRunning()
        } else {
            requestNotificationPermissionIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        deepLinkFromIntent(intent)?.let { MonitorState.setPendingDeepLink(it) }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Keeps the monitor service alive and triggers a debounced poll for new items. */
    private fun ensureNotificationMonitorRunning() {
        val app = application as CommspurxApplication
        app.sessionStore.getSessionSnapshot()?.user?.let { user ->
            app.startNotificationMonitor(user, resetBaseline = false)
            app.requestNotificationPoll()
            BatteryOptimizationHelper.requestExemptionIfNeeded(this)
        }
    }
}
