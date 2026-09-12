package com.example.brightnesscontrol.brightness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.brightnesscontrol.MainActivity
import com.example.brightnesscontrol.R
import com.example.brightnesscontrol.data.AppPreferences

class BrightnessService : Service() {
    private var windowManager: WindowManager? = null
    private var dimView: View? = null
    private var restoreOnStop = false
    private var baseBrightness: Int? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                restoreOnStop = intent.getBooleanExtra(EXTRA_RESTORE, false)
                stopSelf()
            }
            ACTION_APPLY, null -> {
                val preferences = AppPreferences(this).current()
                val correction = intent?.getIntExtra(EXTRA_CORRECTION, preferences.correction)
                    ?: preferences.correction
                baseBrightness = intent?.getIntExtra(
                    EXTRA_BASE,
                    preferences.baseBrightness
                ) ?: preferences.baseBrightness
                applyCorrection(correction, baseBrightness ?: preferences.baseBrightness)
            }
        }
        return START_STICKY
    }

    private fun applyCorrection(correction: Int, base: Int) {
        if (correction > 0) {
            BrightnessController.writeSystemBrightness(
                this,
                BrightnessController.systemValueForCorrection(base, correction)
            )
            removeDimLayer()
        } else if (correction == 0) {
            BrightnessController.writeSystemBrightness(this, base)
            removeDimLayer()
        } else if (BrightnessController.canDrawOverlay(this)) {
            // Keep the system level at the saved base and reduce perceived brightness with a layer.
            BrightnessController.writeSystemBrightness(this, base)
            showDimLayer(BrightnessController.overlayAlphaForCorrection(correction))
        }
    }

    private fun showDimLayer(alpha: Float) {
        val manager = windowManager ?: return
        if (dimView == null) {
            dimView = View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            runCatching { manager.addView(dimView, params) }
                .onFailure { dimView = null }
        }
        dimView?.alpha = alpha.coerceIn(0f, 0.92f)
    }

    private fun removeDimLayer() {
        dimView?.let { view -> runCatching { windowManager?.removeView(view) } }
        dimView = null
    }

    override fun onDestroy() {
        removeDimLayer()
        if (restoreOnStop) baseBrightness?.let { BrightnessController.writeSystemBrightness(this, it) }
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

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_active))
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "brightness_control"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_APPLY = "com.example.brightnesscontrol.APPLY"
        private const val ACTION_STOP = "com.example.brightnesscontrol.STOP"
        private const val EXTRA_CORRECTION = "correction"
        private const val EXTRA_BASE = "base_brightness"
        private const val EXTRA_RESTORE = "restore"

        fun apply(context: Context, correction: Int, baseBrightness: Int) {
            if (!BrightnessController.canDrawOverlay(context) && correction < 0) return
            val intent = Intent(context, BrightnessService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_CORRECTION, correction)
                putExtra(EXTRA_BASE, baseBrightness)
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
