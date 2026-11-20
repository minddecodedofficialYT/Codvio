package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.util.BackgroundSettingsHelper

class AgentForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_WORKING -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildWorkingNotification(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        WORKING_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(WORKING_NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_WORKING_ID = "pocketcode_agent_working"
        const val CHANNEL_COMPLETED_ID = "pocketcode_agent_completed"
        const val WORKING_NOTIFICATION_ID = 4101
        const val COMPLETED_NOTIFICATION_ID = 4102

        const val ACTION_START_WORKING = "com.example.action.START_WORKING"
        const val ACTION_STOP_WORKING = "com.example.action.STOP_WORKING"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val workingChannel = NotificationChannel(
                CHANNEL_WORKING_ID,
                "Active Agent Runs",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while Codvio works in the background"
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                "Task Completions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when an agent task finishes while you are away or locked"
            }

            manager.createNotificationChannel(workingChannel)
            manager.createNotificationChannel(completedChannel)
        }

        fun buildWorkingNotification(context: Context): Notification {
            ensureChannels(context)
            return NotificationCompat.Builder(context, CHANNEL_WORKING_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Codvio")
                .setContentText("Codvio is working…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(createLaunchIntent(context))
                .build()
        }

        fun showTaskCompletedNotification(context: Context, sessionName: String? = null) {
            if (!BackgroundSettingsHelper.areNotificationsAllowed(context)) return
            ensureChannels(context)
            val detail = sessionName?.takeIf { it.isNotBlank() }?.let { "Task completed · $it" }
                ?: "Task completed"
            val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Task completed")
                .setContentText(detail)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(createLaunchIntent(context))
                .build()

            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(COMPLETED_NOTIFICATION_ID, notification)
            }
        }

        private fun createLaunchIntent(context: Context): PendingIntent {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
