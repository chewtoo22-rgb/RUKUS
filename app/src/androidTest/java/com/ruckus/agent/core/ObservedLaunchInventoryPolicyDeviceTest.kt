package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservedLaunchInventoryPolicyDeviceTest {
    @Test
    fun foregroundAppSurvivesBoundedObservationAndCanProveCompletion() {
        val candidates = (0 until 40).map { index ->
            AppLaunchMatchPolicy.Candidate(
                packageName = "com.example.app${index.toString().padStart(2, '0')}",
                label = "App $index"
            )
        }
        val target = candidates.last()
        val selected = ObservedLaunchInventoryPolicy.select(candidates, target.packageName)
        val observation = buildString {
            append("pkg=").append(target.packageName).append(" | ")
            append(selected.joinToString(" • ") { candidate ->
                "app[package=${candidate.packageName};label=${candidate.label}]"
            })
        }
        val plan = CommandPlanner.Plan(
            actions = listOf(AgentAction.OpenAppByName(target.label)),
            rejectedParts = emptyList()
        )

        assertEquals(ObservedLaunchInventoryPolicy.MAX_OBSERVED_APPS, selected.size)
        assertTrue(selected.any { it == target })
        assertTrue(TaskCompletionGate.evaluate(plan, completedSteps = 1, finalScreen = observation).ok)
    }
}
