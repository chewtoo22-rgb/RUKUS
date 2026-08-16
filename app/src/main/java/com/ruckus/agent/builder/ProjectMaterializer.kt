package com.ruckus.agent.builder

import java.io.File

/** Materializes only declared BuildManifest entries inside the app-controlled workspace. */
class ProjectMaterializer(private val workspaceRoot: File) {
    fun materialize(project: ProjectRecord, manifest: BuildManifest): BuildJob {
        val projectDir = File(workspaceRoot, project.id)
        require(projectDir.canonicalPath.startsWith(workspaceRoot.canonicalPath))
        projectDir.mkdirs()

        manifest.files.forEach { generated ->
            val target = File(projectDir, generated.path)
            require(target.canonicalPath.startsWith(projectDir.canonicalPath))
            target.parentFile?.mkdirs()
            if (!target.exists()) target.writeText(generated.seedContent)
        }

        return BuildJob(
            projectId = project.id,
            workspacePath = projectDir.absolutePath,
            state = BuildJobState.READY_TO_BUILD,
            log = listOf("Materialized ${manifest.files.size} declared files")
        )
    }
}
