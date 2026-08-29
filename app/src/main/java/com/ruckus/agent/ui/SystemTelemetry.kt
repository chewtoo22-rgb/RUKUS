package com.ruckus.agent.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import kotlin.math.roundToInt

data class SystemTelemetry(
    val batteryPercent: Int,
    val batteryCharging: Boolean,
    val batteryTempC: Float?,
    val ramUsedPercent: Int,
    val ramUsedGb: Float,
    val ramTotalGb: Float,
    val storageUsedPercent: Int,
    val storageFreeGb: Float,
    val cpuLoadPercent: Int?,
    val thermalStatus: String
)

object SystemTelemetryReader {
    fun read(context: Context): SystemTelemetry {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = tempRaw.takeUnless { it == Int.MIN_VALUE }?.div(10f)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRam = memoryInfo.totalMem.coerceAtLeast(1L)
        val usedRam = totalRam - memoryInfo.availMem
        val ramPercent = ((usedRam.toDouble() / totalRam.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes.coerceAtLeast(1L)
        val freeStorage = stat.availableBytes
        val usedStorage = totalStorage - freeStorage
        val storagePercent = ((usedStorage.toDouble() / totalStorage.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        val cpuLoad = runCatching {
            val oneMinuteLoad = java.io.File("/proc/loadavg").readText().trim().substringBefore(' ').toFloat()
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            ((oneMinuteLoad / cores.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }.getOrNull()

        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Hot"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }

        return SystemTelemetry(
            batteryPercent = batteryPercent,
            batteryCharging = charging,
            batteryTempC = tempC,
            ramUsedPercent = ramPercent,
            ramUsedGb = bytesToGb(usedRam),
            ramTotalGb = bytesToGb(totalRam),
            storageUsedPercent = storagePercent,
            storageFreeGb = bytesToGb(freeStorage),
            cpuLoadPercent = cpuLoad,
            thermalStatus = thermal
        )
    }

    private fun bytesToGb(bytes: Long): Float = bytes / 1_073_741_824f
}
