package com.example.brightnesscontrol.brightness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.brightnesscontrol.data.AppPreferences
import kotlin.math.abs
import kotlin.math.max

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val preferences = AppPreferences(context).current()
        if (!preferences.autostart || !BrightnessController.canWriteSettings(context)) return

        val systemPercent = preferences.brightnessLevel.coerceAtLeast(0)
        val dimPercent = when {
            preferences.brightnessLevel < 0 ->
                max(abs(preferences.brightnessLevel), preferences.dimPercent)
            preferences.brightnessLevel == 0 -> 0
            else -> preferences.dimPercent
        }
        BrightnessService.apply(context, systemPercent, dimPercent)
    }
}
