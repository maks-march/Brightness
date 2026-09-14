package com.example.brightnesscontrol.brightness

/** Pure rules for the signed slider: negative values dim, non-negative values set system brightness. */
object BrightnessPolicy {
    fun systemPercentForLevel(level: Int): Int =
        level.coerceIn(-100, 100).coerceAtLeast(0)

    fun effectiveDimPercent(level: Int, additionalDimPercent: Int): Int {
        val safeLevel = level.coerceIn(-100, 100)
        val safeAdditional = additionalDimPercent.coerceIn(0, 100)
        return when {
            safeLevel < 0 -> maxOf(-safeLevel, safeAdditional)
            safeLevel == 0 -> 0
            else -> safeAdditional
        }
    }

    fun requiresAccessibility(level: Int, additionalDimPercent: Int): Boolean =
        effectiveDimPercent(level, additionalDimPercent) > 0
}
