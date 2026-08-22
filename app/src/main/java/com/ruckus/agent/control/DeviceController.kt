package com.ruckus.agent.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.ruckus.agent.core.AgentAction

class DeviceController(private val context: Context) {
    fun execute(action: AgentAction): Result<String> = runCatching {
        when (action) {
            is AgentAction.OpenApp -> {
                val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: error("Package is not launchable: ${action.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent); "Opened package=${action.packageName}"
            }
            is AgentAction.OpenAppByName -> {
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val match = context.packageManager.queryIntentActivities(query, 0)
                    .map { it to it.loadLabel(context.packageManager).toString() }
                    .firstOrNull { (_, label) -> label.equals(action.appName, true) }
                    ?: context.packageManager.queryIntentActivities(query,0)
                        .map { it to it.loadLabel(context.packageManager).toString() }
                        .firstOrNull { (_, label) -> label.contains(action.appName, true) }
                    ?: error("App not found: ${action.appName}")
                val pkg=match.first.activityInfo.packageName
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: error("App is not launchable: ${match.second}")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); "Opened ${match.second} package=$pkg"
            }
            AgentAction.Back -> { checkNotNull(RuckusAccessibilityService.instance).performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); "Back" }
            AgentAction.Home -> { checkNotNull(RuckusAccessibilityService.instance).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); "Home" }
            is AgentAction.Tap -> { check(RuckusAccessibilityService.instance?.tap(action.x, action.y) == true); "Tapped ${action.x},${action.y}" }
            is AgentAction.TapLabel -> { check(RuckusAccessibilityService.instance?.clickLabel(action.label) == true) { "Visible label not found/clickable: ${action.label}" }; "Tapped ${action.label}" }
            is AgentAction.Swipe -> { check(RuckusAccessibilityService.instance?.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs) == true); "Swiped" }
            is AgentAction.Scroll -> { check(RuckusAccessibilityService.instance?.scroll(action.direction) == true); "Scrolled ${action.direction.name.lowercase()}" }
            is AgentAction.TypeText -> { check(RuckusAccessibilityService.instance?.typeFocused(action.text) == true) { "No editable focused field" }; "Typed text" }
            AgentAction.InspectScreen -> inspectScreen()
            is AgentAction.SetBrightness -> {
                require(action.percent in 0..100); check(Settings.System.canWrite(context)) { "WRITE_SETTINGS not granted" }
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (action.percent * 255 / 100).coerceIn(1,255)); "Brightness ${action.percent}%"
            }
            is AgentAction.SetMediaVolume -> {
                require(action.percent in 0..100)
                val am=context.getSystemService(AudioManager::class.java); val max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC,(max*action.percent/100).coerceIn(0,max),0); "Media ${action.percent}%"
            }
            is AgentAction.RunApprovedShell -> error("Bounded Shizuku adapter not enabled yet")
        }
    }

    private fun inspectScreen(): String {
        val service=checkNotNull(RuckusAccessibilityService.instance) { "Accessibility service offline" }
        val pkg=service.activePackage() ?: "unknown"
        val nodes=service.snapshot().asSequence()
            .filter { node ->
                !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || node.editable || node.focused
            }
            .map { node ->
                val label = node.text?.trim()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.trim()?.takeIf { it.isNotBlank() }
                    ?: ""
                "node[text=${escapeObservation(label)};clickable=${node.clickable};editable=${node.editable};sensitive=${node.sensitive};focused=${node.focused}]"
            }
            .distinct()
            .take(16)
            .toList()
        return "pkg=$pkg | ${if(nodes.isEmpty()) "No readable labels" else nodes.joinToString(" • ")}" 
    }

    private fun escapeObservation(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace("]", "\\]")
        .replace("•", " ")
        .replace("\n", " ")
        .replace("\r", " ")
}
