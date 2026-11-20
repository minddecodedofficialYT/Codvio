package com.example.util

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class BackgroundStatusSnapshot(
    val notificationsAllowed: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val allFilesAccessAllowed: Boolean = false
)

object BackgroundSettingsHelper {

    fun readSnapshot(context: Context): BackgroundStatusSnapshot {
        return BackgroundStatusSnapshot(
            notificationsAllowed = areNotificationsAllowed(context),
            batteryOptimizationIgnored = isBatteryBackgroundAllowed(context),
            allFilesAccessAllowed = StorageUtils.hasStorageAccess(context)
        )
    }

    fun areNotificationsAllowed(context: Context): Boolean {
        val systemEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        if (!systemEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryBackgroundAllowed(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    fun isDeviceLockedOrScreenOff(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val isScreenOff = powerManager?.isInteractive == false
        return isLocked || isScreenOff
    }

    fun openNotificationSettings(context: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        return startSettingsIntent(context, intent) || openAppDetailsSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (startSettingsIntent(context, requestIntent)) return true

        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return startSettingsIntent(context, listIntent) || openAppDetailsSettings(context)
    }

    fun openAllFilesAccessSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (StorageUtils.launchAllFilesAccessSettings(context)) return true
        }
        return openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return startSettingsIntent(context, intent)
    }

    private fun startSettingsIntent(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
