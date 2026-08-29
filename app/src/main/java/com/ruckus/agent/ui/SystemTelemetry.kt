package com.ruckus.agent.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs

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
    private val unavailable = SystemTelemetry(
        batteryPercent = 0,
        batteryCharging = false,
        batteryTempC = null,
        ramUsedPercent = 0,
        ramUsedGb = 0f,
        ramTotalGb = 0f,
        storageUsedPercent = 0,
        storageFreeGb = 0f,
        cpuLoadPercent = null,
        thermalStatus = "Unknown"
    )

    fun read(context: Context): SystemTelemetry {
        val battery = readBattery(context)
        val memory = readMemory(context)
        val storage = readStorage()
        val cpu = readCpu()
        val thermal = readThermal(context)

        return SystemTelemetry(
            batteryPercent = battery?.percent ?: unavailable.batteryPercent,
            batteryCharging = battery?.charging ?: unavailable.batteryCharging,
            batteryTempC = battery?.temperatureC,
            ramUsedPercent = memory?.usedPercent ?: unavailable.ramUsedPercent,
            ramUsedGb = memory?.usedGb ?: unavailable.ramUsedGb,
            ramTotalGb = memory?.totalGb ?: unavailable.ramTotalGb,
            storageUsedPercent = storage?.usedPercent ?: unavailable.storageUsedPercent,
            storageFreeGb = storage?.freeGb ?: unavailable.storageFreeGb,
            cpuLoadPercent = cpu,
            thermalStatus = thermal
        )
    }

    private data class BatterySnapshot(val percent: Int, val charging: Boolean, val temperatureC: Float?)
    private data class MemorySnapshot(val usedPercent: Int, val usedGb: Float, val totalGb: Float)
    private data class StorageSnapshot(val usedPercent: Int, val freeGb: Float)

    private fun readBattery(context: Context): BatterySnapshot? = runCatching {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@runCatching null
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempRaw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        BatterySnapshot(
            percent = TelemetryNormalization.batteryPercent(level, scale),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureC = tempRaw.takeUnless { it == Int.MIN_VALUE }?.div(10f)
        )
    }.getOrNull()

    private fun readMemory(context: Context): MemorySnapshot? = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching null
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val total = info.totalMem.coerceAtLeast(0L)
        val available = info.availMem.coerceIn(0L, total)
        val used = (total - available).coerceAtLeast(0L)
        MemorySnapshot(
            usedPercent = TelemetryNormalization.ratioPercent(used, total),
            usedGb = TelemetryNormalization.nonNegativeGb(used),
            totalGb = TelemetryNormalization.nonNegativeGb(total)
        )
    }.getOrNull()

    private fun readStorage(): StorageSnapshot? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes.coerceAtLeast(0L)
        val free = stat.availableBytes.coerceIn(0L, total)
        val used = (total - free).coerceAtLeast(0L)
        StorageSnapshot(
            usedPercent = TelemetryNormalization.ratioPercent(used, total),
            freeGb = TelemetryNormalization.nonNegativeGb(free)
        )
    }.getOrNull()

    private fun readCpu(): Int? = runCatching {
        val load = java.io.File("/proc/loadavg").readText().trim().substringBefore(' ').toFloat()
        TelemetryNormalization.cpuPercent(load, Runtime.getRuntime().availableProcessors())
    }.getOrNull()

    private fun readThermal(context: Context): String = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching "Unknown"
        when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Hot"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
    }.getOrDefault("Unknown")
}
