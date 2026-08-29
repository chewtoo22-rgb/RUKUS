package com.ruckus.agent.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import rikka.shizuku.Shizuku

/** Android-side executor for the bounded remediation contract. */
object AndroidCapabilityRemediator {
    fun openAccessibilitySettings(context: Context): Boolean =
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openWriteSettings(context: Context): Boolean =
        launch(
            context,
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )

    fun requestShizukuPermission(requestCode: Int): ShizukuPermissionDecision {
        val state = runCatching { ShizukuStateReader.read() }
            .getOrElse { return ShizukuPermissionDecision.SERVICE_UNAVAILABLE }
        val decision = ShizukuPermissionPlanner.decide(state, requestCode)
        if (decision != ShizukuPermissionDecision.REQUEST_PERMISSION) return decision

        return runCatching {
            Shizuku.requestPermission(requestCode)
            ShizukuPermissionDecision.REQUEST_PERMISSION
        }.getOrElse {
            ShizukuPermissionDecision.SERVICE_UNAVAILABLE
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
