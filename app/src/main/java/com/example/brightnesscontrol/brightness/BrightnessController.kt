package com.example.brightnesscontrol.brightness

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

object BrightnessController {
    fun canWriteSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    fun canDrawOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun readSystemBrightness(context: Context): Int {
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128).coerceIn(0, 255)
    }

    fun writeSystemBrightness(context: Context, value: Int): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(0, 255)
            )
        }.getOrDefault(false)
    }

    /** Positive correction moves from the saved base toward 255. */
    fun systemValueForCorrection(base: Int, correction: Int): Int {
        if (correction <= 0) return base.coerceIn(0, 255)
        val room = 255 - base.coerceIn(0, 255)
        return (base + room * (correction / 100f)).roundToInt().coerceIn(0, 255)
    }

    /** A black overlay is intentionally capped below opaque black so the UI remains recoverable. */
    fun overlayAlphaForCorrection(correction: Int): Float {
        return (correction.coerceIn(-100, 0).absoluteValue / 100f * 0.92f)
    }

    fun estimatedPerceivedPercent(base: Int, correction: Int): Int {
        val basePercent = (base / 255f * 100f)
        return if (correction < 0) {
            (basePercent * (1f - overlayAlphaForCorrection(correction))).roundToInt()
        } else {
            (systemValueForCorrection(base, correction) / 255f * 100f).roundToInt()
        }
    }

    private val Int.absoluteValue: Int
        get() = if (this < 0) -this else this
}
