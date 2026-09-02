package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserPackageIdentityTest {
    @Test
    fun exactPackageParsingPreservesIdentifierCase() {
        val parsed = CommandParser.parse("open package Com.Example.MixedCase")

        assertTrue(parsed.action is AgentAction.OpenApp)
        assertEquals("Com.Example.MixedCase", (parsed.action as AgentAction.OpenApp).packageName)
    }

    @Test
    fun commandKeywordRemainsCaseInsensitiveWithoutNormalizingPackageArgument() {
        val parsed = CommandParser.parse("OPEN PACKAGE Com.Example.MixedCase")

        assertEquals("Com.Example.MixedCase", (parsed.action as AgentAction.OpenApp).packageName)
    }

    @Test
    fun plannerPreservesExactPackageIdentityInsideSequence() {
        val plan = CommandPlanner.plan("open package Com.Example.MixedCase then home")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals("Com.Example.MixedCase", (plan.actions.first() as AgentAction.OpenApp).packageName)
        assertTrue(plan.actions.last() is AgentAction.Home)
    }
}
