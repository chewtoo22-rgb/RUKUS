package com.ruckus.agent.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
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
    private lateinit var context: Context
    private lateinit var store: TaskSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun completedTaskEvidenceSurvivesDurableRoundTrip() {
        val state = AgentTaskState(
            request = "open settings then go home",
            currentStep = 2,
            totalSteps = 2,
            lastAction = AgentAction.Home,
            lastScreenSummary = "pkg=com.android.launcher | screen=Home | verified=true",
            recoveryAttempts = 1,
            status = AgentTaskState.Status.COMPLETED
        )

        store.save(state, planFingerprint = "device-test-completed-plan")

        // Re-open through a fresh store instance to model the app process reading
        // terminal evidence after relaunch rather than relying on in-memory state.
        val reloaded = TaskSessionStore(context).load()
        assertNotNull(reloaded)
        reloaded!!
        assertEquals(AgentTaskState.Status.COMPLETED, reloaded.status)
        assertEquals(state.request, reloaded.request)
        assertEquals(state.totalSteps, reloaded.currentStep)
        assertEquals(state.totalSteps, reloaded.totalSteps)
        assertEquals(state.recoveryAttempts, reloaded.recoveryAttempts)
        assertEquals(state.lastAction.toString(), reloaded.lastAction)
        assertEquals(state.lastScreenSummary, reloaded.lastScreenSummary)
        assertEquals("device-test-completed-plan", reloaded.planFingerprint)
        assertTrue(PersistedSessionDigest.matches(reloaded))
    }

    @Test
    fun tamperedDurableCheckpointFailsClosed() {
        val state = AgentTaskState(
            request = "home",
            currentStep = 0,
            totalSteps = 2,
            lastAction = AgentAction.Home,
            lastScreenSummary = "pkg=com.example.before | screen=Home",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.EXECUTING
        )

        store.save(state, planFingerprint = "device-test-plan-fingerprint")

        val prefs = context.getSharedPreferences("ruckus_task_session", Context.MODE_PRIVATE)
        val raw = prefs.getString("active_task", null)
        assertNotNull(raw)

        val tampered = JSONObject(raw!!).apply {
            // Simulate storage/process corruption or local checkpoint manipulation while
            // deliberately leaving the original digest in place.
            put("currentStep", 1)
            put("lastAction", AgentAction.Back.toString())
        }
        assertTrue(prefs.edit().putString("active_task", tampered.toString()).commit())

        // Durable storage is evidence, not execution authority. A checkpoint whose
        // contents no longer match its integrity digest must never be resumed.
        assertNull(store.load())
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
