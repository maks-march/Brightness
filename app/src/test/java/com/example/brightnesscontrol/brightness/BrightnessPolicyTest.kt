package com.example.brightnesscontrol.brightness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessPolicyTest {
    @Test
    fun zeroIsSystemZeroWithoutOverlay() {
        assertEquals(0, BrightnessPolicy.systemPercentForLevel(0))
        assertEquals(0, BrightnessPolicy.effectiveDimPercent(0, 80))
        assertFalse(BrightnessPolicy.requiresAccessibility(0, 80))
    }

    @Test
    fun nonNegativeLevelsControlSystemBrightness() {
        assertEquals(0, BrightnessPolicy.systemPercentForLevel(0))
        assertEquals(50, BrightnessPolicy.systemPercentForLevel(50))
        assertEquals(100, BrightnessPolicy.systemPercentForLevel(100))
        assertEquals(0, BrightnessPolicy.effectiveDimPercent(20, 0))
    }

    @Test
    fun negativeLevelsTurnSystemBrightnessToZeroAndDimByAbsoluteValue() {
        assertEquals(0, BrightnessPolicy.systemPercentForLevel(-1))
        assertEquals(0, BrightnessPolicy.systemPercentForLevel(-100))
        assertEquals(1, BrightnessPolicy.effectiveDimPercent(-1, 0))
        assertEquals(100, BrightnessPolicy.effectiveDimPercent(-100, 0))
        assertTrue(BrightnessPolicy.requiresAccessibility(-1, 0))
    }

    @Test
    fun additionalNotificationDimmingIsCombinedWithNegativeSliderDimming() {
        assertEquals(30, BrightnessPolicy.effectiveDimPercent(-30, 10))
        assertEquals(50, BrightnessPolicy.effectiveDimPercent(-30, 50))
        assertEquals(20, BrightnessPolicy.effectiveDimPercent(60, 20))
    }

    @Test
    fun levelsAndAdditionalDimmingAreClamped() {
        assertEquals(100, BrightnessPolicy.systemPercentForLevel(150))
        assertEquals(0, BrightnessPolicy.systemPercentForLevel(-150))
        assertEquals(100, BrightnessPolicy.effectiveDimPercent(-150, 150))
    }
}
