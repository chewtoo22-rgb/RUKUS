package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class PlanSafetyPreflightTest {
    private fun approvalFor(action: AgentAction): String = PlanSafetyPreflight.approvalFingerprint(action)

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

    @Test fun actionBoundApprovalAllowsWholeRemainingPlanToProceed() {
        val confirm=AgentAction.RunApprovedShell("demo")
        val actions=listOf(AgentAction.Home,confirm)
        val decision=PlanSafetyPreflight.evaluate(
            actions,
            0,
            approved=true,
            approvedActionFingerprint=approvalFor(confirm)
        )
        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test fun approvalForDifferentActionIsRejected() {
        val pending=AgentAction.TapLabel("Send")
        val wrong=AgentAction.TapLabel("Delete photo")
        val decision=PlanSafetyPreflight.evaluate(
            listOf(pending),
            approved=true,
            approvedActionFingerprint=approvalFor(wrong)
        )
        assertFalse(decision.allowed)
        assertTrue(decision.needsConfirmation)
        assertEquals(pending,decision.action)
        assertTrue(decision.reason.contains("did not match"))
    }

    @Test fun bareBooleanApprovalNoLongerAuthorizesHighImpactAction() {
        val pending=AgentAction.TapLabel("Send")
        val decision=PlanSafetyPreflight.evaluate(listOf(pending),approved=true)
        assertFalse(decision.allowed)
        assertTrue(decision.needsConfirmation)
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

    @Test fun actionBoundApprovalAllowsHighImpactSemanticTap() {
        val action=AgentAction.TapLabel("Send")
        val decision=PlanSafetyPreflight.evaluate(
            listOf(action),
            approved=true,
            approvedActionFingerprint=approvalFor(action)
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

    @Test fun multipleConfirmationActionsAreRejectedEvenWithActionFingerprint() {
        val first=AgentAction.TapLabel("Send")
        val actions=listOf(first,AgentAction.RunApprovedShell("demo"))
        val decision=PlanSafetyPreflight.evaluate(
            actions,
            approved=true,
            approvedActionFingerprint=approvalFor(first)
        )
        assertFalse(decision.allowed)
        assertFalse(decision.needsConfirmation)
        assertEquals(0,decision.actionIndex)
        assertEquals(first,decision.action)
        assertTrue(decision.reason.contains("2 confirmation-required actions"))
        assertTrue(decision.reason.contains("separate tasks"))
    }

    @Test fun multipleHighImpactSemanticActionsAreRejectedWithoutBlanketApproval() {
        val first=AgentAction.TapLabel("Delete photo")
        val actions=listOf(AgentAction.Home,first,AgentAction.TapLabel("Share with Alex"))
        val decision=PlanSafetyPreflight.evaluate(
            actions,
            approved=true,
            approvedActionFingerprint=approvalFor(first)
        )
        assertFalse(decision.allowed)
        assertEquals(1,decision.actionIndex)
        assertEquals(first,decision.action)
    }

    @Test fun completedConfirmationDoesNotPoisonResumedRemainder() {
        val remaining=AgentAction.RunApprovedShell("remaining")
        val actions=listOf(
            AgentAction.TapLabel("Send"),
            AgentAction.Home,
            remaining
        )
        val decision=PlanSafetyPreflight.evaluate(
            actions,
            startStep=1,
            approved=true,
            approvedActionFingerprint=approvalFor(remaining)
        )
        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test fun invalidStartStepIsRejected() {
        val actions=listOf(AgentAction.Home)
        assertFalse(PlanSafetyPreflight.evaluate(actions,startStep=-1).allowed)
        assertFalse(PlanSafetyPreflight.evaluate(actions,startStep=2).allowed)
    }
}
