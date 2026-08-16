package com.ruckus.agent.builder

import java.io.File
import java.util.concurrent.TimeUnit

/** Executes only a fixed Gradle task in a materialized workspace. */
class BuildExecutor {
    fun assembleDebug(job: BuildJob): BuildJob {
        val dir = File(job.workspacePath)
        val gradlew = File(dir, "gradlew")
        if (!gradlew.exists()) {
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + "gradlew not present; wrapper provisioning required"
            )
        }

        val process = ProcessBuilder(gradlew.absolutePath, ":app:assembleDebug", "--stacktrace", "--no-daemon")
            .directory(dir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readLines().takeLast(400)
        val finished = process.waitFor(8, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            return job.copy(state = BuildJobState.FAILED, log = job.log + output + "Build timed out")
        }

        val apk = File(dir, "app/build/outputs/apk/debug/app-debug.apk")
        return if (process.exitValue() == 0 && apk.exists()) {
            job.copy(state = BuildJobState.SUCCEEDED, apkPath = apk.absolutePath, log = job.log + output)
        } else {
            job.copy(state = BuildJobState.FAILED, log = job.log + output)
        }
    }
}
