package com.ruckus.agent.builder

enum class BuildKind { ANDROID_APP, GAME_2D, GAME_3D }

data class ProjectSpec(
    val name: String,
    val packageName: String,
    val kind: BuildKind,
    val description: String,
    val features: List<String> = emptyList()
)
