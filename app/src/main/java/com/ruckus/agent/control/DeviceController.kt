package com.ruckus.agent.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.ruckus.agent.core.AgentAction
import com.ruckus.agent.core.AppLaunchMatchPolicy
import com.ruckus.agent.core.BrightnessMutationPolicy
import com.ruckus.agent.core.InstalledAppLaunchResolver
import com.ruckus.agent.core.MediaVolumeMutationPolicy
import com.ruckus.agent.core.ObservedLaunchInventoryPolicy
import com.ruckus.agent.core.SystemNavigationResult

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
                val match = InstalledAppLaunchResolver.resolve(context, action.appName)
                    ?: error("App name is unavailable or ambiguous: ${action.appName}")
                val launch = context.packageManager.getLaunchIntentForPackage(match.packageName)
                    ?: error("App is no longer launchable: ${match.label}")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                "Opened ${match.label} package=${match.packageName}"
            }
            AgentAction.Back -> {
                val service = checkNotNull(RuckusAccessibilityService.instance) { "Accessibility service offline" }
                SystemNavigationResult.requirePerformed(
                    actionName = "Back",
                    performed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                )
            }
            AgentAction.Home -> {
                val service = checkNotNull(RuckusAccessibilityService.instance) { "Accessibility service offline" }
                SystemNavigationResult.requirePerformed(
                    actionName = "Home",
                    performed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                )
            }
            is AgentAction.Tap -> { check(RuckusAccessibilityService.instance?.tap(action.x, action.y) == true); "Tapped ${action.x},${action.y}" }
            is AgentAction.TapLabel -> { check(RuckusAccessibilityService.instance?.clickLabel(action.label) == true) { "Visible label not found/clickable/enabled: ${action.label}" }; "Tapped ${action.label}" }
            is AgentAction.Swipe -> { check(RuckusAccessibilityService.instance?.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs) == true); "Swiped" }
            is AgentAction.Scroll -> { check(RuckusAccessibilityService.instance?.scroll(action.direction) == true); "Scrolled ${action.direction.name.lowercase()}" }
            is AgentAction.TypeText -> { check(RuckusAccessibilityService.instance?.typeFocused(action.text) == true) { "No enabled, editable, non-sensitive focused field" }; "Typed text" }
            AgentAction.InspectScreen -> inspectScreen()
            is AgentAction.SetBrightness -> {
                require(action.percent in 0..100)
                check(Settings.System.canWrite(context)) { "WRITE_SETTINGS not granted" }
                val applied = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    BrightnessMutationPolicy.toSystemValue(action.percent)
                )
                BrightnessMutationPolicy.requireApplied(applied, action.percent)
                "Brightness ${action.percent}%"
            }
            is AgentAction.SetMediaVolume -> {
                require(action.percent in 0..100)
                val audio = context.getSystemService(AudioManager::class.java)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = MediaVolumeMutationPolicy.targetIndex(action.percent, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                MediaVolumeMutationPolicy.requireApplied(target, actual, action.percent)
                "Media ${action.percent}%"
            }
            is AgentAction.RunApprovedShell -> error("Bounded Shizuku adapter not enabled yet")
        }
    }

    private fun inspectScreen(): String {
        val service=checkNotNull(RuckusAccessibilityService.instance) { "Accessibility service offline" }
        val pkg=service.activePackage() ?: "unknown"
        val nodes=ObservedUiNodePolicy.select(service.snapshot())
            .map { node ->
                val label = ObservationRedactionPolicy.label(node)
                "node[text=${escapeObservation(label)};clickable=${node.clickable};enabled=${node.enabled};editable=${node.editable};sensitive=${node.sensitive};focused=${node.focused};scrollable=${node.scrollable}]"
            }
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchCandidates = context.packageManager.queryIntentActivities(launcherQuery, 0).mapNotNull { info ->
            val packageName = info.activityInfo?.packageName?.trim().orEmpty()
            val label = runCatching { info.loadLabel(context.packageManager).toString().trim() }.getOrDefault("")
            if (packageName.isBlank() || label.isBlank()) null
            else AppLaunchMatchPolicy.Candidate(packageName, label)
        }
        val apps = ObservedLaunchInventoryPolicy.select(launchCandidates, pkg)
            .map { candidate ->
                "app[package=${escapeObservation(candidate.packageName)};label=${escapeObservation(candidate.label)}]"
            }
        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(-1)
        val audio = context.getSystemService(AudioManager::class.java)
        val media = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)
        val mediaMax = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)
        val deviceState = "state[brightness=$brightness;media=$media;mediaMax=$mediaMax]"
        val body = (listOf(deviceState) + nodes + apps).joinToString(" • ")
        return "pkg=$pkg | $body"
    }

    private fun escapeObservation(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace("]", "\\]")
        .replace("•", " ")
        .replace("\n", " ")
        .replace("\r", " ")
}
