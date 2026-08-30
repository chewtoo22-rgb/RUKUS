package com.ruckus.agent.builder

import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Executes only a fixed Gradle task in a materialized workspace. */
class BuildExecutor(
    private val timeout: Long = 8,
    private val timeoutUnit: TimeUnit = TimeUnit.MINUTES
) {
    fun assembleDebug(job: BuildJob): BuildJob {
        val dir = File(job.workspacePath)
        val gradlew = File(dir, "gradlew")
        if (!gradlew.isFile) {
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + "gradlew not present; wrapper provisioning required"
            )
        }
        if (!gradlew.canExecute()) {
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + "gradlew is not executable"
            )
        }

        val process = try {
            ProcessBuilder(gradlew.absolutePath, ":app:assembleDebug", "--stacktrace", "--no-daemon")
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + "Unable to start Gradle wrapper: ${error.javaClass.simpleName}"
            )
        }

        val output = BoundedBuildLog(400)
        val reader = thread(name = "rukus-builder-log-${job.id}", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach(output::append)
                }
            } catch (_: Exception) {
                // Process teardown may close the stream. Build outcome remains authoritative.
            }
        }

        val finished = try {
            process.waitFor(timeout, timeoutUnit)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!finished) {
            terminateProcessTree(process)
            reader.join(2_000)
            return job.copy(
                state = BuildJobState.FAILED,
                log = job.log + output.snapshot() + "Build timed out"
            )
        }

        reader.join(2_000)
        val apk = File(dir, "app/build/outputs/apk/debug/app-debug.apk")
        val captured = output.snapshot()
        return if (process.exitValue() == 0 && apk.isFile) {
            job.copy(state = BuildJobState.SUCCEEDED, apkPath = apk.absolutePath, log = job.log + captured)
        } else {
            job.copy(state = BuildJobState.FAILED, log = job.log + captured)
        }
    }

    private fun terminateProcessTree(process: Process) {
        // Gradle wrappers normally exec a JVM, but custom/generated wrappers can spawn children.
        // Kill descendants first so they cannot inherit stdout and keep the log reader alive.
        val descendants = process.toHandle().descendants().toList().asReversed()
        descendants.forEach { handle ->
            if (handle.isAlive) handle.destroyForcibly()
        }
        if (process.isAlive) process.destroyForcibly()
        try {
            process.waitFor(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

internal class BoundedBuildLog(private val maxLines: Int) {
    init {
        require(maxLines > 0)
    }

    private val lines = ArrayDeque<String>(maxLines)

    @Synchronized
    fun append(line: String) {
        if (lines.size == maxLines) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
