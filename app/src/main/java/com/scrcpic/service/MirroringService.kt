package com.scrcpic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scrcpic.MainActivity
import com.scrcpic.R

class MirroringService : Service {
    constructor() : super()

    companion object {
        private const val TAG = "MirroringService"
        private const val CHANNEL_ID = "scrcpic_mirroring_channel"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_TARGET_ADDRESS = "extra_target_address"

        fun start(context: Context, targetAddress: String) {
            try {
                val intent = Intent(context, MirroringService::class.java).apply {
                    putExtra(EXTRA_TARGET_ADDRESS, targetAddress)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to launch MirroringService (graceful fallback): ${t.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MirroringService::class.java)
                context.stopService(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to stop MirroringService: ${t.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            acquireLocks()
        } catch (t: Throwable) {
            Log.w(TAG, "onCreate init error: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val targetAddress = intent?.getStringExtra(EXTRA_TARGET_ADDRESS) ?: "Target Device"
            val notification = buildNotification(targetAddress)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground error (graceful fallback): ${t.message}")
        }

        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Scrcpic:WakeLock").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Scrcpic:WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Throwable) {}
        wifiLock = null
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Active Mirroring Session",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps wireless remote mirroring active in background"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create notification channel: ${t.message}")
        }
    }

    private fun buildNotification(targetAddress: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val logoBitmap = try {
            BitmapFactory.decodeResource(resources, R.drawable.app_logo)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scrcpic Active Mirroring")
            .setContentText("Connected to $targetAddress")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (logoBitmap != null) {
            builder.setLargeIcon(logoBitmap)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        Log.d(TAG, "MirroringService destroyed, locks released")
    }
}
