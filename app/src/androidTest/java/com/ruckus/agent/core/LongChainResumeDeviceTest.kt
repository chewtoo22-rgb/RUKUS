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

/**
 * Device/emulator probe for long-chain crash-safe resume near the admitted horizon.
 *
 * CI compiles this into the instrumentation APK. Execution remains a Thursday
 * device/emulator test so no physical-runtime result is implied by compilation.
 */
@RunWith(AndroidJUnit4::class)
class LongChainResumeDeviceTest {
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
    fun nearMaximumChainResumesAtFirstUnverifiedStepWithRecoveryBudgetIntact() {
        val request = "home then back then home then back then home then back then home then back"
        val plan = CommandPlanner.plan(request)

        assertEquals(PlanAdmissionPolicy.MAX_ACTIONS, plan.actions.size)
        assertTrue(plan.rejectedParts.isEmpty())

        // Model six verified actions in an eight-action task, including two recovery
        // attempts already consumed before process death. The persisted checkpoint
        // must resume at step seven without replaying the six verified actions or
        // resetting the task-wide recovery budget.
        val state = AgentTaskState(
            request = request,
            currentStep = 6,
            totalSteps = plan.actions.size,
            lastAction = plan.actions[5],
            lastScreenSummary = "pkg=com.android.launcher | screen=Home | verifiedSteps=6",
            recoveryAttempts = 2,
            status = AgentTaskState.Status.RUNNING
        )
        val fingerprint = PlanFingerprint.of(plan)
        store.save(state, planFingerprint = fingerprint)

        val reloaded = TaskSessionStore(context).load()
        assertNotNull(reloaded)
        reloaded!!
        assertEquals(fingerprint, reloaded.planFingerprint)
        assertEquals(6, reloaded.currentStep)
        assertEquals(2, reloaded.recoveryAttempts)
        assertTrue(PersistedSessionDigest.matches(reloaded))

        val decision = ResumePolicy.decide(reloaded, CommandPlanner.plan(reloaded.request))
        assertTrue(decision.allowed)
        assertEquals(6, decision.startStep)

        val remainingBudget = RecoveryBudget.decide(reloaded.recoveryAttempts)
        assertTrue(remainingBudget.allowed)
        assertEquals(
            "Recovery attempt 3/${RecoveryBudget.MAX_TOTAL_ATTEMPTS} allowed",
            remainingBudget.reason
        )
    }
}
