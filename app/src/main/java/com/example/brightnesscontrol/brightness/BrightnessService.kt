package com.example.brightnesscontrol.brightness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.brightnesscontrol.MainActivity
import com.example.brightnesscontrol.R
import com.example.brightnesscontrol.data.AppPreferences

/** Foreground service keeps the notification/actions alive; the actual overlay belongs to the accessibility service. */
class BrightnessService : Service() {
    private var restoreOnStop = false
    private var basePercent: Int? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                restoreOnStop = intent.getBooleanExtra(EXTRA_RESTORE, false)
                stopSelf()
            }
            ACTION_ADJUST -> adjustFromNotification(intent.getIntExtra(EXTRA_DELTA, 0))
            ACTION_APPLY, null -> {
                val preferences = AppPreferences(this).current()
                val correction = intent?.getIntExtra(EXTRA_CORRECTION, preferences.correction)
                    ?: preferences.correction
                basePercent = intent?.getIntExtra(
                    EXTRA_BASE,
                    preferences.basePercent
                ) ?: preferences.basePercent
                applyCorrection(correction, basePercent ?: preferences.basePercent)
                updateForegroundNotification(correction)
            }
        }
        return START_STICKY
    }

    private fun adjustFromNotification(delta: Int) {
        val preferences = AppPreferences(this).current()
        val correction = (preferences.correction + delta).coerceIn(-100, 0)
        basePercent = preferences.basePercent
        AppPreferences(this).setCorrection(correction)

        if (correction == 0) {
            BrightnessController.writeSystemBrightness(this, preferences.basePercent)
            BrightnessAccessibilityService.clearOverlay()
            stopSelf()
            return
        }

        applyCorrection(correction, preferences.basePercent)
        updateForegroundNotification(correction)
    }

    private fun applyCorrection(correction: Int, base: Int) {
        when {
            correction > 0 -> {
                BrightnessController.writeSystemBrightness(
                    this,
                    BrightnessController.systemPercentForCorrection(base, correction)
                )
                BrightnessAccessibilityService.clearOverlay()
            }
            correction == 0 -> {
                BrightnessController.writeSystemBrightness(this, base)
                BrightnessAccessibilityService.clearOverlay()
            }
            else -> {
                // Keep the hardware level at the saved base and dim perceived brightness via accessibility overlay.
                BrightnessController.writeSystemBrightness(this, base)
                BrightnessAccessibilityService.applyOverlay(
                    BrightnessController.overlayAlphaForCorrection(correction)
                )
            }
        }
    }

    private fun updateForegroundNotification(correction: Int) {
        startForeground(NOTIFICATION_ID, buildNotification(correction))
    }

    override fun onDestroy() {
        BrightnessAccessibilityService.clearOverlay()
        if (restoreOnStop) basePercent?.let { BrightnessController.writeSystemBrightness(this, it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.background_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(correction: Int): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dimPercent = (-correction).coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_dim_level, dimPercent))
            .setContentIntent(launchIntent)
            .setProgress(100, dimPercent, false)
            .addAction(
                R.drawable.ic_remove,
                getString(R.string.notification_decrease),
                adjustPendingIntent(-10)
            )
            .addAction(
                R.drawable.ic_add,
                getString(R.string.notification_increase),
                adjustPendingIntent(10)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun adjustPendingIntent(delta: Int): PendingIntent {
        val intent = Intent(this, BrightnessService::class.java).apply {
            action = ACTION_ADJUST
            putExtra(EXTRA_DELTA, delta)
        }
        val requestCode = if (delta < 0) REQUEST_DECREASE else REQUEST_INCREASE
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "brightness_control"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_APPLY = "com.example.brightnesscontrol.APPLY"
        private const val ACTION_STOP = "com.example.brightnesscontrol.STOP"
        private const val ACTION_ADJUST = "com.example.brightnesscontrol.ADJUST"
        private const val EXTRA_CORRECTION = "correction"
        private const val EXTRA_BASE = "base_brightness"
        private const val EXTRA_RESTORE = "restore"
        private const val EXTRA_DELTA = "delta"
        private const val REQUEST_DECREASE = 4102
        private const val REQUEST_INCREASE = 4103

        fun apply(context: Context, correction: Int, basePercent: Int) {
            if (correction >= 0 || !BrightnessAccessibilityService.isEnabled(context)) return
            val intent = Intent(context, BrightnessService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_CORRECTION, correction)
                putExtra(EXTRA_BASE, basePercent)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, restore: Boolean) {
            val intent = Intent(context, BrightnessService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_RESTORE, restore)
            }
            // startService delivers ACTION_STOP even when the service is already running.
            runCatching { context.startService(intent) }
        }
    }
}
