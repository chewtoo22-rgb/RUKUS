package com.ruckus.agent.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.ruckus.agent.core.AgentAction

class DeviceController(private val context: Context) {
    fun execute(action: AgentAction): Result<String> = runCatching {
        when (action) {
            is AgentAction.OpenApp -> {
                require(action.packageName.isNotBlank()) { "Package name must not be blank" }
                val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: error("Package is not launchable: ${action.packageName}")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opened ${action.packageName}"
            }
            AgentAction.Back -> {
                val service = RuckusAccessibilityService.instance
                    ?: error("Accessibility service is not connected")
                check(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    "Accessibility back action was rejected"
                }
                "Back"
            }
            AgentAction.Home -> {
                val service = RuckusAccessibilityService.instance
                    ?: error("Accessibility service is not connected")
                check(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                    "Accessibility home action was rejected"
                }
                "Home"
            }
            is AgentAction.Tap -> {
                require(action.x.isFinite() && action.y.isFinite()) { "Tap coordinates must be finite" }
                check(RuckusAccessibilityService.instance?.tap(action.x, action.y) == true) {
                    "Tap action was rejected or accessibility service is unavailable"
                }
                "Tapped ${action.x},${action.y}"
            }
            is AgentAction.Swipe -> {
                require(action.x1.isFinite() && action.y1.isFinite() && action.x2.isFinite() && action.y2.isFinite()) {
                    "Swipe coordinates must be finite"
                }
                require(action.durationMs > 0) { "Swipe duration must be positive" }
                check(RuckusAccessibilityService.instance?.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs) == true) {
                    "Swipe action was rejected or accessibility service is unavailable"
                }
                "Swiped"
            }
            is AgentAction.TypeText -> error("Text injection adapter is queued for Phase 1")
            is AgentAction.SetBrightness -> {
                require(action.percent in 0..100)
                check(Settings.System.canWrite(context)) { "WRITE_SETTINGS not granted" }
                val value = (action.percent * 255 / 100).coerceIn(1, 255)
                check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)) {
                    "Brightness update was rejected"
                }
                "Brightness ${action.percent}%"
            }
            is AgentAction.SetMediaVolume -> {
                require(action.percent in 0..100) { "Media volume must be between 0 and 100" }
                val am = context.getSystemService(AudioManager::class.java)
                    ?: error("Audio service is unavailable")
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * action.percent / 100).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                "Media ${action.percent}%"
            }
            is AgentAction.RunApprovedShell -> error("Shizuku shell adapter is scaffolded but intentionally not wired to arbitrary commands")
        }
    }
}
