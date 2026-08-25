package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedLaunchInventoryPolicyTest {
    @Test
    fun `foreground package is retained beyond normal observation cap`() {
        val candidates = (0 until 40).map { index ->
            AppLaunchMatchPolicy.Candidate(
                packageName = "com.example.app${index.toString().padStart(2, '0')}",
                label = "App $index"
            )
        }
        val foreground = candidates.last()

        val selected = ObservedLaunchInventoryPolicy.select(candidates, foreground.packageName)

        assertEquals(ObservedLaunchInventoryPolicy.MAX_OBSERVED_APPS, selected.size)
        assertTrue(selected.any { it == foreground })
        assertEquals(foreground, selected.first())
    }

    @Test
    fun `inventory stays bounded and deterministic without foreground match`() {
        val candidates = (39 downTo 0).map { index ->
            AppLaunchMatchPolicy.Candidate(
                packageName = "com.example.app${index.toString().padStart(2, '0')}",
                label = "App $index"
            )
        }

        val selected = ObservedLaunchInventoryPolicy.select(candidates, "com.example.missing")

        assertEquals(ObservedLaunchInventoryPolicy.MAX_OBSERVED_APPS, selected.size)
        assertEquals("com.example.app00", selected.first().packageName)
        assertEquals("com.example.app31", selected.last().packageName)
    }

    @Test
    fun `invalid and duplicate launcher entries cannot consume observation budget`() {
        val valid = AppLaunchMatchPolicy.Candidate("com.example.target", "Target")
        val candidates = listOf(
            AppLaunchMatchPolicy.Candidate("", "Blank package"),
            AppLaunchMatchPolicy.Candidate("com.example.blank", ""),
            valid,
            valid
        )

        assertEquals(listOf(valid), ObservedLaunchInventoryPolicy.select(candidates, valid.packageName))
    }
}
