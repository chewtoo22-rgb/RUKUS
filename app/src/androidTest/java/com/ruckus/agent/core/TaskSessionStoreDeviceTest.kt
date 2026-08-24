package com.ruckus.agent.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator probe for the durable executor checkpoint boundary.
 *
 * CI compiles this test APK as a hard build gate; execution is intentionally left
 * for an Android target so Thursday hands-on testing can verify the real
 * SharedPreferences persistence path rather than a JVM approximation.
 */
@RunWith(AndroidJUnit4::class)
class TaskSessionStoreDeviceTest {
    private lateinit var store: TaskSessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = TaskSessionStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun executingCheckpointRoundTripsWithIntegrityMetadata() {
        val state = AgentTaskState(
            request = "home",
            currentStep = 0,
            totalSteps = 1,
            lastAction = AgentAction.Home,
            lastScreenSummary = "pkg=com.example.before | screen=Home",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.EXECUTING
        )

        store.save(state, planFingerprint = "device-test-plan-fingerprint")

        val loaded = store.load()
        assertNotNull(loaded)
        loaded!!
        assertEquals(state.request, loaded.request)
        assertEquals(state.currentStep, loaded.currentStep)
        assertEquals(state.totalSteps, loaded.totalSteps)
        assertEquals(state.lastAction.toString(), loaded.lastAction)
        assertEquals(state.lastScreenSummary, loaded.lastScreenSummary)
        assertEquals(state.status, loaded.status)
        assertEquals("device-test-plan-fingerprint", loaded.planFingerprint)
        assertEquals(PERSISTED_SESSION_SCHEMA_VERSION, loaded.schemaVersion)
        assertTrue(!loaded.checkpointDigest.isNullOrBlank())
        assertTrue(PersistedSessionDigest.matches(loaded))
    }

    @Test
    fun clearRemovesDurableCheckpoint() {
        val state = AgentTaskState(
            request = "home",
            currentStep = 0,
            totalSteps = 1,
            lastAction = AgentAction.Home,
            lastScreenSummary = null,
            recoveryAttempts = 0,
            status = AgentTaskState.Status.EXECUTING
        )

        store.save(state, planFingerprint = "device-test-plan-fingerprint")
        assertNotNull(store.load())

        store.clear()

        assertNull(store.load())
    }
}
