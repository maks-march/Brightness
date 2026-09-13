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

/** Foreground service keeps the dimming controls in the notification. */
class BrightnessService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BrightnessAccessibilityService.clearOverlay()
                stopSelf()
            }
            ACTION_ADJUST -> adjustDimFromNotification(intent.getIntExtra(EXTRA_DELTA, 0))
            ACTION_APPLY, null -> {
                val preferences = AppPreferences(this).current()
                val brightnessPercent = intent?.getIntExtra(
                    EXTRA_BRIGHTNESS,
                    preferences.brightnessLevel.coerceAtLeast(0)
                ) ?: preferences.brightnessLevel.coerceAtLeast(0)
                val dimPercent = intent?.getIntExtra(
                    EXTRA_DIM,
                    effectiveDimPercent(preferences.brightnessLevel, preferences.dimPercent)
                ) ?: effectiveDimPercent(preferences.brightnessLevel, preferences.dimPercent)
                applySettings(brightnessPercent, dimPercent)
                updateForegroundNotification(dimPercent)
            }
        }
        return START_STICKY
    }

    private fun adjustDimFromNotification(delta: Int) {
        val preferences = AppPreferences(this)
        var preferencesState = preferences.current()
        val dimPercent = (preferencesState.dimPercent + delta).coerceIn(0, 100)
        preferences.setDimPercent(dimPercent)
        preferencesState = preferences.current()
        val effectiveDim = effectiveDimPercent(preferencesState.brightnessLevel, dimPercent)
        applySettings(preferencesState.brightnessLevel.coerceAtLeast(0), effectiveDim)
        if (dimPercent == 0 && preferencesState.brightnessLevel >= 0) {
            stopSelf()
        } else {
            updateForegroundNotification(effectiveDim)
        }
    }

    private fun effectiveDimPercent(brightnessLevel: Int, additionalDimPercent: Int): Int = when {
        brightnessLevel < 0 -> maxOf(-brightnessLevel, additionalDimPercent)
        brightnessLevel == 0 -> 0
        else -> additionalDimPercent
    }

    private fun applySettings(brightnessPercent: Int, dimPercent: Int) {
        BrightnessController.writeSystemBrightness(this, brightnessPercent.coerceIn(0, 100))
        if (dimPercent > 0 && BrightnessAccessibilityService.isEnabled(this)) {
            BrightnessAccessibilityService.applyOverlay(
                BrightnessController.overlayAlphaForDimPercent(dimPercent)
            )
        } else {
            BrightnessAccessibilityService.clearOverlay()
        }
    }

    override fun onDestroy() {
        BrightnessAccessibilityService.clearOverlay()
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

    private fun buildNotification(dimPercent: Int): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dim = dimPercent.coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_dim_level, dim))
            .setContentIntent(launchIntent)
            .setProgress(100, dim, false)
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

    private fun updateForegroundNotification(dimPercent: Int) {
        startForeground(NOTIFICATION_ID, buildNotification(dimPercent))
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
        private const val EXTRA_BRIGHTNESS = "brightness_percent"
        private const val EXTRA_DIM = "dim_percent"
        private const val EXTRA_DELTA = "delta"
        private const val REQUEST_DECREASE = 4102
        private const val REQUEST_INCREASE = 4103

        fun apply(context: Context, brightnessPercent: Int, dimPercent: Int) {
            val intent = Intent(context, BrightnessService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_BRIGHTNESS, brightnessPercent.coerceIn(0, 100))
                putExtra(EXTRA_DIM, dimPercent.coerceIn(0, 100))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrightnessService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
