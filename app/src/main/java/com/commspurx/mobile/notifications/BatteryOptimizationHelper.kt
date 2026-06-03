package com.commspurx.mobile.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Many OEMs block background alarms/work until battery optimization is disabled. */
object BatteryOptimizationHelper {
    private const val PREFS = "commspurx_prefs"
    private const val KEY_REQUESTED = "battery_opt_requested"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestExemptionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations(activity)) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_REQUESTED, true).apply()

        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                },
            )
        }
    }

    /** Opens system battery settings so the user can allow unrestricted background work. */
    fun openBatterySettings(activity: Activity) {
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            },
        )
        for (intent in intents) {
            if (runCatching { activity.startActivity(intent) }.isSuccess) return
        }
    }
}
