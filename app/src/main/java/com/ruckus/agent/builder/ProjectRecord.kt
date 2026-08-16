package com.ruckus.agent.builder

data class ProjectRecord(
    val id: String,
    val spec: ProjectSpec,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val status: ProjectStatus = ProjectStatus.PLANNING
)

enum class ProjectStatus {
    PLANNING,
    GENERATING,
    READY_TO_BUILD,
    BUILDING,
    FAILED,
    BUILT,
    TESTING,
    COMPLETE
}
