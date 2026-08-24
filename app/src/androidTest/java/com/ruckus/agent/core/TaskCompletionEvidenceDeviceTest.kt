package com.ruckus.agent.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android storage probe for the terminal completion-proof boundary. */
@RunWith(AndroidJUnit4::class)
class TaskCompletionEvidenceDeviceTest {
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
    fun completedTaskCarriesDurableEvidenceBoundToTerminalState() {
        val state = AgentTaskState(
            request = "open settings then go home",
            currentStep = 2,
            totalSteps = 2,
            lastAction = AgentAction.Home,
            lastScreenSummary = "pkg=com.android.launcher | screen=Home | verified=true",
            recoveryAttempts = 1,
            status = AgentTaskState.Status.COMPLETE
        )

        store.save(state, planFingerprint = "device-proof-plan")

        val loaded = TaskSessionStore(context).load()
        assertNotNull(loaded)
        loaded!!
        assertEquals(AgentTaskState.Status.COMPLETE, loaded.status)
        assertTrue(!loaded.completionEvidenceDigest.isNullOrBlank())
        assertTrue(TaskCompletionEvidence.matches(loaded))
        assertTrue(PersistedSessionDigest.matches(loaded))
        assertNotNull(TaskCompletionEvidence.shortId(loaded))
    }
}
