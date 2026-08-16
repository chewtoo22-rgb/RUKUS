package com.ruckus.agent.builder

import java.io.File

/**
 * Contract for an on-device AndroidIDE/Termux-style build worker.
 * RUKUS remains the control plane; build tools live in the worker environment.
 */
class AndroidIdeBuildBackend(
    private val workerHome: File?,
    private val aapt2Path: File?
) : BuildBackend {
    override fun status(): BuildBackendStatus {
        val homeOk = workerHome?.exists() == true
        val aaptOk = aapt2Path?.exists() == true
        return BuildBackendStatus(
            kind = BuildBackendKind.ANDROIDIDE_WORKER,
            available = homeOk && aaptOk,
            detail = if (homeOk && aaptOk) {
                "AndroidIDE-compatible worker detected"
            } else {
                "AndroidIDE worker not provisioned"
            }
        )
    }

    override fun build(job: BuildJob): BuildJob {
        val state = status()
        if (!state.available) {
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + state.detail
            )
        }

        return job.copy(
            state = BuildJobState.FAILED,
            log = job.log + "Worker transport not connected yet; execution intentionally blocked"
        )
    }
}
