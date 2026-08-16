package com.ruckus.agent.builder

/** Explicit contract between MUTINY build output and RUKUS device testing. */
data class RukusHandoff(
    val projectId: String,
    val apkPath: String,
    val packageName: String,
    val launchAfterInstall: Boolean = true,
    val runSmokeTest: Boolean = true
)

object HandoffFactory {
    fun from(job: BuildJob, record: ProjectRecord): RukusHandoff {
        require(job.state == BuildJobState.SUCCEEDED)
        val apk = requireNotNull(job.apkPath)
        return RukusHandoff(
            projectId = record.id,
            apkPath = apk,
            packageName = record.spec.packageName
        )
    }
}
