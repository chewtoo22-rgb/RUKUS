package com.ruckus.agent.control

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStateReaderTest {
    @Test
    fun `unavailable binder is reported fail closed without permission query`() {
        var permissionChecks = 0
        val api = object : ShizukuStatusApi {
            override fun pingBinder() = false
            override fun checkSelfPermission(): Int {
                permissionChecks += 1
                return PackageManager.PERMISSION_GRANTED
            }
        }

        assertEquals(
            ShizukuState(binderAvailable = false, permissionGranted = false),
            ShizukuStateReader.read(api)
        )
        assertEquals(0, permissionChecks)
    }

    @Test
    fun `live binder with granted permission is available`() {
        val api = object : ShizukuStatusApi {
            override fun pingBinder() = true
            override fun checkSelfPermission() = PackageManager.PERMISSION_GRANTED
        }

        assertEquals(
            ShizukuState(binderAvailable = true, permissionGranted = true),
            ShizukuStateReader.read(api)
        )
    }

    @Test
    fun `binder loss during permission query fails closed instead of escaping`() {
        val api = object : ShizukuStatusApi {
            override fun pingBinder() = true
            override fun checkSelfPermission(): Int = throw IllegalStateException("binder died")
        }

        assertEquals(
            ShizukuState(binderAvailable = false, permissionGranted = false),
            ShizukuStateReader.read(api)
        )
    }

    @Test
    fun `permission query denial preserves binder availability`() {
        val api = object : ShizukuStatusApi {
            override fun pingBinder() = true
            override fun checkSelfPermission() = PackageManager.PERMISSION_DENIED
        }

        assertEquals(
            ShizukuState(binderAvailable = true, permissionGranted = false),
            ShizukuStateReader.read(api)
        )
    }
}
