package com.ruckus.agent.builder

import com.ruckus.agent.builder.NitroBuildJobState.Event
import com.ruckus.agent.builder.NitroBuildJobState.Snapshot
import com.ruckus.agent.builder.NitroBuildJobState.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NitroBuildJobStateTest {
    @Test fun happyPathRequiresEveryGate() {
        var s = Snapshot()
        s = NitroBuildJobState.reduce(s, Event.START_GENERATION)
        s = NitroBuildJobState.reduce(s, Event.GENERATION_COMPLETE)
        s = NitroBuildJobState.reduce(s, Event.STATIC_CHECKS_PASSED)
        s = NitroBuildJobState.reduce(s, Event.APK_BUILD_COMPLETE)
        s = NitroBuildJobState.reduce(s, Event.APK_VALIDATED)
        assertEquals(State.READY, s.state)
        assertTrue(s.terminal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotSkipStaticChecks() {
        val generating = NitroBuildJobState.reduce(Snapshot(), Event.START_GENERATION)
        NitroBuildJobState.reduce(generating, Event.STATIC_CHECKS_PASSED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotDeclareReadyBeforeApkValidation() {
        val building = Snapshot(State.BUILDING_APK)
        NitroBuildJobState.reduce(building, Event.APK_VALIDATED)
    }

    @Test fun failureRecordsExactStage() {
        val checking = Snapshot(State.STATIC_CHECKING)
        val failed = NitroBuildJobState.reduce(checking, Event.FAIL)
        assertEquals(State.FAILED, failed.state)
        assertEquals(State.STATIC_CHECKING, failed.failureStage)
        assertTrue(failed.terminal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun queuedJobCannotInventFailureEvidence() {
        NitroBuildJobState.reduce(Snapshot(), Event.FAIL)
    }

    @Test fun cancellationIsTerminalFromActiveState() {
        val active = Snapshot(State.BUILDING_APK)
        val cancelled = NitroBuildJobState.reduce(active, Event.CANCEL)
        assertEquals(State.CANCELLED, cancelled.state)
        assertTrue(cancelled.terminal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun terminalReadyCannotRestart() {
        NitroBuildJobState.reduce(Snapshot(State.READY), Event.START_GENERATION)
    }

    @Test fun activeStatesAreNonTerminal() {
        listOf(State.QUEUED, State.GENERATING, State.STATIC_CHECKING, State.BUILDING_APK, State.VALIDATING_APK)
            .forEach { assertFalse(Snapshot(it).terminal) }
    }
}
