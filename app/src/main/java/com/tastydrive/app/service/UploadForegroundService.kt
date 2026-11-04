package com.tastydrive.app.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tastydrive.app.R
import com.tastydrive.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UploadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationUpdateJob: Job? = null
    private lateinit var uploadManager: UploadManager

    override fun onCreate() {
        super.onCreate()
        uploadManager = UploadManager.getInstance(this)
        createNotificationChannel()
        acquireWakeLock()
        startForegroundWithNotification()
        observeUploadUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> uploadManager.pauseUploads()
            ACTION_RESUME -> uploadManager.resumeUploads()
            ACTION_STOP -> {
                uploadManager.stopUploads()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Crucial: When the user swipes away the app from the recent apps page,
        // we DO NOT kill this service. The foreground service with notification
        // remains active and continues uploading in the background!
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Tasty Drive is preparing uploads...", 0, 0, "0 B/s")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeUploadUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (true) {
                delay(1200)
                val stats = uploadManager.stats.value
                val activeItem = uploadManager.getCurrentlyUploadingItem()

                if (!stats.isUploading && !stats.isPaused && stats.pendingCount == 0) {
                    // Uploads finished!
                    val notif = buildCompletedNotification(stats.completedCount)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notif)
                    break
                }

                val title = if (stats.isPaused) {
                    "Tasty Drive (Paused)"
                } else if (activeItem != null) {
                    "Uploading: ${activeItem.displayName}"
                } else {
                    "Tasty Drive Background Sync"
                }

                val content = if (stats.isPaused) {
                    "Uploads paused • Remaining: ${stats.pendingCount}"
                } else {
                    "Remaining: ${stats.pendingCount} | Sent: ${stats.completedCount} • ${stats.currentSpeedStr}"
                }

                val currentProgress = activeItem?.progress ?: stats.progressPercent
                val notification = buildNotification(
                    title = title,
                    progress = currentProgress,
                    max = 100,
                    subText = content
                )

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        max: Int,
        subText: String
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = uploadManager.isPaused.value
        val toggleActionIntent = Intent(this, UploadForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopActionIntent = Intent(this, UploadForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasty_notification)
            .setContentTitle(title)
            .setContentText(subText)
            .setContentIntent(openPendingIntent)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(toggleIcon, toggleTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildCompletedNotification(totalSent: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasty_notification)
            .setContentTitle("Tasty Drive - Uploads Finished! 🍔🚀")
            .setContentText("All $totalSent photos and videos were successfully backed up to Telegram.")
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tasty Drive Background Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress and speed of media uploading to Telegram"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TastyDrive::UploadWakeLock"
            ).apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "tasty_drive_upload_channel"
        const val NOTIFICATION_ID = 4291

        const val ACTION_PAUSE = "com.tastydrive.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.tastydrive.app.ACTION_RESUME"
        const val ACTION_STOP = "com.tastydrive.app.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, UploadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, UploadForegroundService::class.java)
            context.stopService(intent)
        }
    }
}

