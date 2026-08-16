package com.ruckus.agent.builder

enum class BuildBackendKind {
    ANDROIDIDE_WORKER,
    GITHUB_ACTIONS,
    UNAVAILABLE
}

data class BuildBackendStatus(
    val kind: BuildBackendKind,
    val available: Boolean,
    val detail: String
)

interface BuildBackend {
    fun status(): BuildBackendStatus
    fun build(job: BuildJob): BuildJob
}
