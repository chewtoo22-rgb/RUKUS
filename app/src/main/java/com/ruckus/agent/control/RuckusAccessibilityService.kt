package com.ruckus.agent.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class RuckusAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: RuckusAccessibilityService? = null }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun snapshot(): List<UiNodeSnapshot> = AccessibilitySelectors.flatten(rootInActiveWindow)
    fun clickLabel(label: String): Boolean = AccessibilitySelectors.clickByLabel(rootInActiveWindow, label)
    fun typeFocused(text: String): Boolean = AccessibilitySelectors.typeIntoFocused(rootInActiveWindow, text)

    fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 50)).build()
        return dispatchGesture(g, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, duration)).build()
        return dispatchGesture(g, null, null)
    }
}
