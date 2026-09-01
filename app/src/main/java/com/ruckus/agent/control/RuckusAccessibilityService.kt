package com.ruckus.agent.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.ruckus.agent.core.AgentAction

class RuckusAccessibilityService : AccessibilityService() {
    companion object {
        private val registry = ActiveInstanceRegistry<RuckusAccessibilityService>()
        val instance: RuckusAccessibilityService?
            get() = registry.current()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registry.register(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        registry.unregister(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        registry.unregister(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun snapshot(): List<UiNodeSnapshot> = AccessibilitySelectors.flatten(rootInActiveWindow)
    fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()
    fun clickLabel(label: String): Boolean = AccessibilitySelectors.clickByLabel(rootInActiveWindow, label)
    fun typeFocused(text: String): Boolean = AccessibilitySelectors.typeIntoFocused(rootInActiveWindow, text)

    fun scroll(direction: AgentAction.Direction): Boolean =
        AccessibilitySelectors.scrollUnique(rootInActiveWindow, direction)

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
