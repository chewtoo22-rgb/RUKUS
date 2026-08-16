package com.ruckus.agent.control

import rikka.shizuku.Shizuku

data class ShizukuState(
    val binderAvailable: Boolean,
    val permissionGranted: Boolean
)

object ShizukuStateReader {
    fun read(): ShizukuState {
        val binder = Shizuku.pingBinder()
        val granted = binder && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        return ShizukuState(binderAvailable = binder, permissionGranted = granted)
    }
}
