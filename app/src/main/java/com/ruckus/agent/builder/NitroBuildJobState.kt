package com.ruckus.agent.builder

/** Pure state contract for NITRO build jobs. No process, filesystem, Android, or network effects. */
object NitroBuildJobState {
    enum class State {
        QUEUED,
        GENERATING,
        STATIC_CHECKING,
        BUILDING_APK,
        VALIDATING_APK,
        READY,
        FAILED,
        CANCELLED
    }

    enum class Event {
        START_GENERATION,
        GENERATION_COMPLETE,
        STATIC_CHECKS_PASSED,
        APK_BUILD_COMPLETE,
        APK_VALIDATED,
        FAIL,
        CANCEL
    }

    data class Snapshot(
        val state: State = State.QUEUED,
        val failureStage: State? = null
    ) {
        val terminal: Boolean
            get() = state == State.READY || state == State.FAILED || state == State.CANCELLED
    }

    fun reduce(snapshot: Snapshot, event: Event): Snapshot {
        require(!snapshot.terminal) { "terminal build job cannot transition" }

        if (event == Event.FAIL) {
            require(snapshot.state != State.QUEUED) { "queued job cannot fail before work starts" }
            return Snapshot(State.FAILED, failureStage = snapshot.state)
        }
        if (event == Event.CANCEL) {
            return Snapshot(State.CANCELLED)
        }

        val next = when (snapshot.state to event) {
            State.QUEUED to Event.START_GENERATION -> State.GENERATING
            State.GENERATING to Event.GENERATION_COMPLETE -> State.STATIC_CHECKING
            State.STATIC_CHECKING to Event.STATIC_CHECKS_PASSED -> State.BUILDING_APK
            State.BUILDING_APK to Event.APK_BUILD_COMPLETE -> State.VALIDATING_APK
            State.VALIDATING_APK to Event.APK_VALIDATED -> State.READY
            else -> throw IllegalArgumentException("illegal build transition: ${snapshot.state} + $event")
        }
        return Snapshot(next)
    }
}
