package com.example.brightnesscontrol.brightness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.brightnesscontrol.data.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val preferences = AppPreferences(context).current()
        if (!preferences.autostart || !BrightnessController.canWriteSettings(context)) return

        val systemPercent = BrightnessPolicy.systemPercentForLevel(preferences.brightnessLevel)
        val dimPercent = BrightnessPolicy.effectiveDimPercent(
            preferences.brightnessLevel,
            preferences.dimPercent
        )
        BrightnessService.apply(context, systemPercent, dimPercent)
    }
}
