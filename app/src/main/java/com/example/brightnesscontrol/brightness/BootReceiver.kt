package com.example.brightnesscontrol.brightness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.brightnesscontrol.data.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val preferences = AppPreferences(context).current()
        if (!preferences.autostart || preferences.correction == 0) return

        if (preferences.correction < 0) {
            if (!BrightnessAccessibilityService.isEnabled(context)) return
            val serviceIntent = Intent(context, BrightnessService::class.java).apply {
                action = "com.example.brightnesscontrol.APPLY"
                putExtra("correction", preferences.correction)
                putExtra("base_brightness", preferences.basePercent)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else if (BrightnessController.canWriteSettings(context)) {
            BrightnessController.writeSystemBrightness(
                context,
                BrightnessController.systemPercentForCorrection(
                    preferences.basePercent,
                    preferences.correction
                )
            )
        }
    }
}
