package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIdentifierPolicyTest {
    @Test
    fun acceptsStructurallyValidExactPackageWithoutNormalizingIdentity() {
        assertTrue(PackageIdentifierPolicy.isValid("Com.Example.MixedCase_2"))

        val parsed = CommandParser.parse("OPEN PACKAGE Com.Example.MixedCase_2")
        assertEquals("Com.Example.MixedCase_2", (parsed.action as AgentAction.OpenApp).packageName)
    }

    @Test
    fun rejectsMalformedPackageIdentifiersBeforeCreatingExecutableAction() {
        val invalid = listOf(
            "",
            "android",
            ".com.example",
            "com..example",
            "com.example.",
            "1com.example",
            "com.2example",
            "com.example-name",
            "com.example name",
            "com.example\nother"
        )

        invalid.forEach { packageName ->
            assertFalse("expected invalid: $packageName", PackageIdentifierPolicy.isValid(packageName))
            val parsed = CommandParser.parse("open package $packageName")
            assertNull("parser created action for malformed package: $packageName", parsed.action)
        }
    }

    @Test
    fun plannerKeepsMalformedExactPackageAsRejectionEvidenceOnly() {
        val plan = CommandPlanner.plan("open package com.bad-name then home")

        assertEquals(listOf(AgentAction.Home), plan.actions)
        assertEquals(listOf("open package com.bad-name"), plan.rejectedParts)
    }

    @Test
    fun rejectsOverlongIdentifier() {
        val overlong = "com." + "a".repeat(252)
        assertTrue(overlong.length > 255)
        assertFalse(PackageIdentifierPolicy.isValid(overlong))
    }
}
