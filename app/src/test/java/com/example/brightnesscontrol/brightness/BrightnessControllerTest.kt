package com.example.brightnesscontrol.brightness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessControllerTest {
    @Test
    fun convertsUserPercentToAndroidRange() {
        assertEquals(0, BrightnessController.rawForPercent(0))
        assertEquals(128, BrightnessController.rawForPercent(50))
        assertEquals(255, BrightnessController.rawForPercent(100))
    }

    @Test
    fun clampsPercentBeforeConverting() {
        assertEquals(0, BrightnessController.rawForPercent(-20))
        assertEquals(255, BrightnessController.rawForPercent(120))
    }

    @Test
    fun convertsAndroidRangeBackToUserPercent() {
        assertEquals(0, BrightnessController.percentForRaw(0))
        assertEquals(50, BrightnessController.percentForRaw(128))
        assertEquals(100, BrightnessController.percentForRaw(255))
    }

    @Test
    fun clampsRawValuesBeforeConverting() {
        assertEquals(0, BrightnessController.percentForRaw(-1))
        assertEquals(100, BrightnessController.percentForRaw(300))
    }

    @Test
    fun overlayAlphaIsCappedAndScalesWithDimPercent() {
        assertEquals(0f, BrightnessController.overlayAlphaForDimPercent(0), 0.0001f)
        assertEquals(0.46f, BrightnessController.overlayAlphaForDimPercent(50), 0.0001f)
        assertEquals(0.92f, BrightnessController.overlayAlphaForDimPercent(100), 0.0001f)
        assertTrue(BrightnessController.overlayAlphaForDimPercent(150) <= 0.92f)
    }
}
