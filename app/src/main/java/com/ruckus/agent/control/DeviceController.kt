package com.ruckus.agent.control

import android.content.Context
import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.provider.Settings
import com.ruckus.agent.core.AgentAction

class DeviceController(private val context: Context) {
    fun execute(action: AgentAction): Result<String> = runCatching {
        when (action) {
            is AgentAction.OpenApp -> {
                val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: error("Package is not launchable: ${action.packageName}")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opened ${action.packageName}"
            }
            AgentAction.Back -> {
                checkNotNull(RuckusAccessibilityService.instance).performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "Back"
            }
            AgentAction.Home -> {
                checkNotNull(RuckusAccessibilityService.instance).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "Home"
            }
            is AgentAction.Tap -> {
                check(RuckusAccessibilityService.instance?.tap(action.x, action.y) == true)
                "Tapped ${action.x},${action.y}"
            }
            is AgentAction.Swipe -> {
                check(RuckusAccessibilityService.instance?.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs) == true)
                "Swiped"
            }
            is AgentAction.TypeText -> error("Text injection adapter is queued for Phase 1")
            is AgentAction.SetBrightness -> {
                require(action.percent in 0..100)
                check(Settings.System.canWrite(context)) { "WRITE_SETTINGS not granted" }
                val value = (action.percent * 255 / 100).coerceIn(1, 255)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                "Brightness ${action.percent}%"
            }
            is AgentAction.SetMediaVolume -> {
                val am = context.getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * action.percent / 100).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                "Media ${action.percent}%"
            }
            is AgentAction.RunApprovedShell -> error("Shizuku shell adapter is scaffolded but intentionally not wired to arbitrary commands")
        }
    }
}
