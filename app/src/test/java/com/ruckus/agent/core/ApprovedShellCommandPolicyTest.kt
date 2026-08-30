package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedShellCommandPolicyTest {
    @Test
    fun admitsKnownReadOnlyCapabilityWithoutArguments() {
        val decision = ApprovedShellCommandPolicy.evaluate("network_interfaces", emptyMap())
        assertTrue(decision.allowed)
    }

    @Test
    fun rejectsUnknownCommandId() {
        val decision = ApprovedShellCommandPolicy.evaluate("rm_everything", emptyMap())
        assertFalse(decision.allowed)
    }

    @Test
    fun rejectsArgumentInjectionIntoNoArgumentCapability() {
        val decision = ApprovedShellCommandPolicy.evaluate(
            "device_uptime",
            mapOf("extra" to "; reboot")
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun planAdmissionRejectsStructurallyValidButUnregisteredShellCommand() {
        val decision = PlanAdmissionPolicy.evaluate(
            listOf(AgentAction.RunApprovedShell("arbitrary_short_id"))
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun planAdmissionAcceptsRegisteredShellCapability() {
        val decision = PlanAdmissionPolicy.evaluate(
            listOf(AgentAction.RunApprovedShell("thermal_status"))
        )
        assertTrue(decision.allowed)
    }
}
