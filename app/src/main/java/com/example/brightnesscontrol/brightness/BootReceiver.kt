package com.example.brightnesscontrol.brightness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.brightnesscontrol.data.AppPreferences
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val preferences = AppPreferences(context).current()
        if (!preferences.autostart || !preferences.backgroundEnabled || preferences.correction == 0) return
        if (preferences.correction < 0 && !BrightnessController.canDrawOverlay(context)) return

        val serviceIntent = Intent(context, BrightnessService::class.java).apply {
            action = "com.example.brightnesscontrol.APPLY"
            putExtra("correction", preferences.correction)
            putExtra("base_brightness", preferences.baseBrightness)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
