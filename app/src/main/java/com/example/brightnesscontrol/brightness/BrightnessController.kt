package com.example.brightnesscontrol.brightness

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

object BrightnessController {
    fun canWriteSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    /** Returns brightness as a user-facing percentage from 0 to 100. */
    fun readSystemBrightness(context: Context): Int {
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return rawToPercent(raw)
    }

    /** Accepts only a 0..100 percentage and converts it to Android's 0..255 storage range. */
    fun writeSystemBrightness(context: Context, percent: Int): Boolean {
        if (!canWriteSettings(context)) return false
        val raw = (percent.coerceIn(0, 100) / 100f * 255f).roundToInt()
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                raw.coerceIn(0, 255)
            )
        }.getOrDefault(false)
    }

    /** Positive correction moves from the hidden base percentage toward 100. */
    fun systemPercentForCorrection(basePercent: Int, correction: Int): Int {
        if (correction <= 0) return basePercent.coerceIn(0, 100)
        val base = basePercent.coerceIn(0, 100)
        return (base + (100 - base) * (correction / 100f)).roundToInt().coerceIn(0, 100)
    }

    /** A black overlay is intentionally capped below opaque black so the UI remains recoverable. */
    fun overlayAlphaForCorrection(correction: Int): Float {
        return (correction.coerceIn(-100, 0).absoluteValue / 100f * 0.92f)
    }

    private fun rawToPercent(raw: Int): Int =
        (raw.coerceIn(0, 255) / 255f * 100f).roundToInt().coerceIn(0, 100)

    private val Int.absoluteValue: Int
        get() = if (this < 0) -this else this
}
