package com.ruckus.agent.control

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShizukuState(
    val binderAvailable: Boolean,
    val permissionGranted: Boolean
)

internal interface ShizukuStatusApi {
    fun pingBinder(): Boolean
    fun checkSelfPermission(): Int
}

private object AndroidShizukuStatusApi : ShizukuStatusApi {
    override fun pingBinder(): Boolean = Shizuku.pingBinder()
    override fun checkSelfPermission(): Int = Shizuku.checkSelfPermission()
}

object ShizukuStateReader {
    fun read(): ShizukuState = read(AndroidShizukuStatusApi)

    internal fun read(api: ShizukuStatusApi): ShizukuState {
        return try {
            if (!api.pingBinder()) {
                ShizukuState(binderAvailable = false, permissionGranted = false)
            } else {
                val granted = api.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                ShizukuState(binderAvailable = true, permissionGranted = granted)
            }
        } catch (_: RuntimeException) {
            // Shizuku's binder can disappear between pingBinder() and the permission query.
            // Treat an unstable service as unavailable so readiness and execution gates fail closed.
            ShizukuState(binderAvailable = false, permissionGranted = false)
        }
    }
}
