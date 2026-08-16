package com.ruckus.agent.builder

import java.io.File

/** Coordinates MUTINY's bounded build pipeline. */
class BuildCoordinator(private val workspaceRoot: File) {
    private val materializer = ProjectMaterializer(workspaceRoot)
    private val executor = BuildExecutor()

    fun prepare(record: ProjectRecord): BuildJob {
        val manifest = ManifestGenerator().generate(record)
        return materializer.materialize(record, manifest)
    }

    fun build(prepared: BuildJob): BuildJob {
        require(prepared.state == BuildJobState.READY_TO_BUILD)
        return executor.assembleDebug(prepared.copy(state = BuildJobState.BUILDING))
    }

    fun handoff(job: BuildJob, record: ProjectRecord): RukusHandoff =
        HandoffFactory.from(job, record)
}
