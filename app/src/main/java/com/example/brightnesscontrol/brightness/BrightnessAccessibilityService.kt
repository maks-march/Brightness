package com.example.brightnesscontrol.brightness

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Overlay backed by the user-enabled Accessibility Service. Unlike an application overlay,
 * this window is eligible to stay above more system surfaces on some Android builds.
 */
class BrightnessAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var dimView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_ANNOUNCEMENT
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        windowManager = getSystemService(WindowManager::class.java)
        requestedAlpha.takeIf { it > 0f }?.let(::showDimLayer)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeDimLayer()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun showDimLayer(alpha: Float) {
        val manager = windowManager ?: return
        if (dimView == null) {
            dimView = View(this).apply { setBackgroundColor(Color.BLACK) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            runCatching { manager.addView(dimView, params) }
                .onFailure { dimView = null }
        }
        dimView?.alpha = alpha.coerceIn(0f, 0.92f)
    }

    private fun removeDimLayer() {
        dimView?.let { view -> runCatching { windowManager?.removeView(view) } }
        dimView = null
    }

    companion object {
        @Volatile
        private var instance: BrightnessAccessibilityService? = null

        @Volatile
        private var requestedAlpha: Float = 0f

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
                ?: return false
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )?.any { info ->
                info.resolveInfo?.serviceInfo?.let { serviceInfo ->
                    ComponentName(serviceInfo.packageName, serviceInfo.name) ==
                        ComponentName(context, BrightnessAccessibilityService::class.java)
                } == true
            } == true
        }

        fun applyOverlay(alpha: Float) {
            requestedAlpha = alpha.coerceIn(0f, 0.92f)
            if (requestedAlpha > 0f) instance?.showDimLayer(requestedAlpha)
            else instance?.removeDimLayer()
        }

        fun clearOverlay() = applyOverlay(0f)
    }
}
