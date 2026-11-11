package com.ruckus.agent.builder

/**
 * Phase-0 contract for RUCKUS Builder.
 * The production implementation will delegate file generation/builds to a sandboxed worker,
 * never directly execute model-supplied shell text on the phone.
 */
class BuilderEngine {
    fun plan(spec: ProjectSpec): List<String> {
        val admitted = NitroProjectAdmission.admit(spec)
        return buildList {
            add("Create ${admitted.kind} project: ${admitted.name}")
            add("Generate package ${admitted.packageName}")
            add("Generate architecture and UI skeleton")
            admitted.features.forEach { add("Implement feature: $it") }
            add("Run static checks")
            add("Build debug APK")
            add("Install/test through Device Agent")
            add("Collect failures and patch")
        }
    }
}
