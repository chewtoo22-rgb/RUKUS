package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class PlanSafetyPreflightTest {
    @Test fun laterConfirmationBlocksBeforeEarlierSafeStepsRun() {
        val actions=listOf(
            AgentAction.Home,
            AgentAction.SetMediaVolume(25),
            AgentAction.RunApprovedShell("demo")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,0,approved=false)
        assertFalse(decision.allowed)
        assertTrue(decision.needsConfirmation)
        assertEquals(2,decision.actionIndex)
        assertTrue(decision.action is AgentAction.RunApprovedShell)
    }

    @Test fun approvalAllowsWholeRemainingPlanToProceed() {
        val actions=listOf(
            AgentAction.Home,
            AgentAction.RunApprovedShell("demo")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,0,approved=true)
        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test fun resumedPlanOnlyPreflightsRemainingActions() {
        val actions=listOf(
            AgentAction.RunApprovedShell("already-completed"),
            AgentAction.Home,
            AgentAction.SetMediaVolume(30)
        )
        val decision=PlanSafetyPreflight.evaluate(actions,startStep=1,approved=false)
        assertTrue(decision.allowed)
    }

    @Test fun highImpactSemanticTapRequiresConfirmationBeforeEarlierActionsRun() {
        val actions=listOf(
            AgentAction.SetMediaVolume(20),
            AgentAction.TapLabel("  Delete   Account  ")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,0,approved=false)
        assertFalse(decision.allowed)
        assertTrue(decision.needsConfirmation)
        assertEquals(1,decision.actionIndex)
        assertEquals(AgentAction.TapLabel("  Delete   Account  "),decision.action)
        assertTrue(decision.reason.contains("High-impact semantic action 'delete account'"))
    }

    @Test fun parameterizedHighImpactLabelsRequireConfirmation() {
        val labels=listOf(
            "Delete photo",
            "Send $20.00",
            "Install update",
            "Post comment",
            "Share with Alex",
            "Call now",
            "Yes, purchase item"
        )
        labels.forEach { label ->
            assertEquals(label, Risk.CONFIRM, SafetyGate.classify(AgentAction.TapLabel(label)).risk)
        }
    }

    @Test fun highImpactMatchingUsesWordBoundaries() {
        val safeLabels=listOf(
            "Payment methods",
            "Installation help",
            "Sender details",
            "Postage options",
            "Callback settings"
        )
        safeLabels.forEach { label ->
            assertEquals(label, Risk.SAFE, SafetyGate.classify(AgentAction.TapLabel(label)).risk)
        }
    }

    @Test fun approvalAllowsHighImpactSemanticTap() {
        val decision=PlanSafetyPreflight.evaluate(
            listOf(AgentAction.TapLabel("Send")),
            approved=true
        )
        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test fun ordinarySemanticTapRemainsRoutine() {
        val decision=PlanSafetyPreflight.evaluate(
            listOf(AgentAction.TapLabel("Continue")),
            approved=false
        )
        assertTrue(decision.allowed)
        assertEquals(Risk.SAFE,SafetyGate.classify(AgentAction.TapLabel("Continue")).risk)
    }

    @Test fun multipleConfirmationActionsAreRejectedEvenWhenApproved() {
        val actions=listOf(
            AgentAction.TapLabel("Send"),
            AgentAction.RunApprovedShell("demo")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,approved=true)
        assertFalse(decision.allowed)
        assertFalse(decision.needsConfirmation)
        assertEquals(0,decision.actionIndex)
        assertEquals(AgentAction.TapLabel("Send"),decision.action)
        assertTrue(decision.reason.contains("2 confirmation-required actions"))
        assertTrue(decision.reason.contains("separate tasks"))
    }

    @Test fun multipleHighImpactSemanticActionsAreRejectedWithoutBlanketApproval() {
        val actions=listOf(
            AgentAction.Home,
            AgentAction.TapLabel("Delete photo"),
            AgentAction.TapLabel("Share with Alex")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,approved=true)
        assertFalse(decision.allowed)
        assertEquals(1,decision.actionIndex)
        assertEquals(AgentAction.TapLabel("Delete photo"),decision.action)
    }

    @Test fun completedConfirmationDoesNotPoisonResumedRemainder() {
        val actions=listOf(
            AgentAction.TapLabel("Send"),
            AgentAction.Home,
            AgentAction.RunApprovedShell("remaining")
        )
        val decision=PlanSafetyPreflight.evaluate(actions,startStep=1,approved=true)
        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test fun invalidStartStepIsRejected() {
        val actions=listOf(AgentAction.Home)
        assertFalse(PlanSafetyPreflight.evaluate(actions,startStep=-1).allowed)
        assertFalse(PlanSafetyPreflight.evaluate(actions,startStep=2).allowed)
    }
}
