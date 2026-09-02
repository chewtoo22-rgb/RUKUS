package com.ruckus.agent.builder

import com.ruckus.agent.builder.NitroProjectStateMigration.PersistedProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NitroProjectStateMigrationTest {
    private fun state(
        schema: Int = 1,
        product: String = "rukus",
        name: String = " Demo App ",
        packageName: String = "com.example.demo",
        kind: String = "android_app",
        description: String = " demo ",
        features: List<String> = listOf(" login ", "offline")
    ) = PersistedProjectState(schema, product, name, packageName, kind, description, features)

    @Test fun migratesLegacyRukusStateToNitroV2() {
        val result = NitroProjectStateMigration.migrate(state())
        assertTrue(result.migrated)
        assertEquals(2, result.state.schemaVersion)
        assertEquals("nitro", result.state.product)
        assertEquals("Demo App", result.state.name)
        assertEquals("ANDROID_APP", result.state.kind)
        assertEquals(listOf("login", "offline"), result.state.features)
    }

    @Test fun currentNitroStateIsIdempotent() {
        val input = state(schema = 2, product = "nitro")
        val once = NitroProjectStateMigration.migrate(input)
        val twice = NitroProjectStateMigration.migrate(once.state)
        assertFalse(once.migrated)
        assertFalse(twice.migrated)
        assertEquals(once.state, twice.state)
    }

    @Test fun currentStateCanonicalizesCaseAndOuterWhitespace() {
        val result = NitroProjectStateMigration.migrate(state(schema = 2, product = "  NITRO  "))
        assertFalse(result.migrated)
        assertEquals("nitro", result.state.product)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFutureSchema() = Unit.also {
        NitroProjectStateMigration.migrate(state(schema = 3, product = "nitro"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLegacyStateWithWrongProductIdentity() = Unit.also {
        NitroProjectStateMigration.migrate(state(product = "nitro"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCurrentStateWithLegacyProductIdentity() = Unit.also {
        NitroProjectStateMigration.migrate(state(schema = 2, product = "rukus"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalLikePackageName() = Unit.also {
        NitroProjectStateMigration.migrate(state(packageName = "com.example../demo"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateFeaturesAfterNormalization() = Unit.also {
        NitroProjectStateMigration.migrate(state(features = listOf("offline", " offline ")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharacters() = Unit.also {
        NitroProjectStateMigration.migrate(state(name = "demo\u0000app"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBuildKind() = Unit.also {
        NitroProjectStateMigration.migrate(state(kind = "WEB_APP"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooManyFeatures() = Unit.also {
        NitroProjectStateMigration.migrate(state(features = (1..33).map { "feature$it" }))
    }
}
