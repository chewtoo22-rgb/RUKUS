package com.ruckus.agent.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStoreMigrationTest {
    @Test
    fun `copies legacy string projects into NITRO namespace`() {
        val planned = ProjectStoreMigration.planCopy(
            legacyEntries = mapOf("p1" to "encoded-one", "p2" to "encoded-two"),
            currentKeys = emptySet()
        )

        assertEquals(mapOf("p1" to "encoded-one", "p2" to "encoded-two"), planned)
    }

    @Test
    fun `never overwrites an existing NITRO project`() {
        val planned = ProjectStoreMigration.planCopy(
            legacyEntries = mapOf("existing" to "legacy", "new" to "legacy-new"),
            currentKeys = setOf("existing")
        )

        assertFalse(planned.containsKey("existing"))
        assertEquals("legacy-new", planned["new"])
    }

    @Test
    fun `ignores malformed non-string preference values`() {
        val planned = ProjectStoreMigration.planCopy(
            legacyEntries = mapOf("valid" to "record", "counter" to 3, "flag" to true),
            currentKeys = emptySet()
        )

        assertEquals(setOf("valid"), planned.keys)
    }

    @Test
    fun `ignores blank legacy keys`() {
        val planned = ProjectStoreMigration.planCopy(
            legacyEntries = mapOf("" to "record", "valid" to "record-two"),
            currentKeys = emptySet()
        )

        assertTrue("valid" in planned)
        assertFalse("" in planned)
    }

    @Test
    fun `storage namespaces are explicit and distinct`() {
        assertEquals("nitro_projects", ProjectStoreMigration.CURRENT_NAMESPACE)
        assertEquals("mutiny_projects", ProjectStoreMigration.LEGACY_NAMESPACE)
        assertFalse(ProjectStoreMigration.CURRENT_NAMESPACE == ProjectStoreMigration.LEGACY_NAMESPACE)
    }
}
