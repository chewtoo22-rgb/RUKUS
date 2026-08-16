package com.ruckus.agent.builder

import java.util.UUID

enum class BuildJobState { QUEUED, MATERIALIZING, READY_TO_BUILD, BUILDING, SUCCEEDED, FAILED, HANDED_TO_RUKUS }

data class BuildJob(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val workspacePath: String,
    val state: BuildJobState = BuildJobState.QUEUED,
    val apkPath: String? = null,
    val log: List<String> = emptyList()
)
