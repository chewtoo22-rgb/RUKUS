package com.ruckus.agent.core

/**
 * Pure active-agent state for the shared RUKUS/NITRO runtime.
 *
 * UI may render this state, but it must not invent agent transitions itself. In particular,
 * NITRO's install/test flow can temporarily hand control to RUKUS and safely return without
 * leaving a stale automatic handoff armed after an explicit user selection.
 */
data class ActiveAgentState(
    val active: AgentIdentity = AgentIdentity.RUKUS,
    val returnTarget: AgentIdentity? = null,
    val revision: Long = 0L
) {
    init {
        require(active == active.canonical()) { "active identity must be canonical" }
        require(returnTarget == null || returnTarget == returnTarget.canonical()) {
            "return target must be canonical"
        }
        require(returnTarget != active) { "return target must differ from active identity" }
        require(revision >= 0L) { "revision must be non-negative" }
    }
}

class ActiveAgentController(initialIdentity: AgentIdentity = AgentIdentity.RUKUS) {
    private var state = ActiveAgentState(active = initialIdentity.canonical())

    @Synchronized
    fun snapshot(): ActiveAgentState = state

    /** Explicit selection always cancels any pending automatic return. */
    @Synchronized
    fun select(identity: AgentIdentity): ActiveAgentState {
        val canonical = identity.canonical()
        if (state.active == canonical && state.returnTarget == null) return state
        state = ActiveAgentState(
            active = canonical,
            returnTarget = null,
            revision = state.revision + 1
        )
        return state
    }

    /**
     * NITRO delegates Android install/test work to RUKUS. The handoff is fail-closed unless
     * NITRO is currently active, preventing unrelated callers from arming an automatic return.
     */
    @Synchronized
    fun beginInstallTestHandoff(): ActiveAgentState {
        check(state.active == AgentIdentity.NITRO) {
            "install/test handoff requires NITRO to be active"
        }
        check(state.returnTarget == null) { "an agent handoff is already pending" }
        state = ActiveAgentState(
            active = AgentIdentity.RUKUS,
            returnTarget = AgentIdentity.NITRO,
            revision = state.revision + 1
        )
        return state
    }

    /** Returns to NITRO only for a still-valid NITRO -> RUKUS handoff. */
    @Synchronized
    fun completeInstallTestHandoff(): ActiveAgentState {
        check(state.active == AgentIdentity.RUKUS && state.returnTarget == AgentIdentity.NITRO) {
            "no NITRO install/test handoff is pending"
        }
        state = ActiveAgentState(
            active = AgentIdentity.NITRO,
            returnTarget = null,
            revision = state.revision + 1
        )
        return state
    }

    /** Abort clears the automatic return without changing the currently active agent. */
    @Synchronized
    fun abortHandoff(): ActiveAgentState {
        if (state.returnTarget == null) return state
        state = ActiveAgentState(
            active = state.active,
            returnTarget = null,
            revision = state.revision + 1
        )
        return state
    }
}
