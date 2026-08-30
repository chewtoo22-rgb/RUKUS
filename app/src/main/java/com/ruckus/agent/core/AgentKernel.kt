package com.ruckus.agent.core

/** Shared runtime beneath both in-app agents. */
class AgentKernel {
    fun capabilities(identity: AgentIdentity): List<String> = when (identity.canonical()) {
        AgentIdentity.RUKUS -> listOf(
            "read_screen", "click_text", "type_text", "tap", "swipe",
            "open_app", "back", "home", "device_settings", "approved_shizuku"
        )
        AgentIdentity.NITRO -> listOf(
            "create_project", "plan_project", "edit_project", "build_apk",
            "analyze_build", "game_2d", "game_3d", "install_test_via_rukus"
        )
        AgentIdentity.MUTINY -> error("canonical() must eliminate legacy MUTINY identity")
    }
}
