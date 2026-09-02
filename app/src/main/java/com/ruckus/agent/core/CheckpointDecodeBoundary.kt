package com.ruckus.agent.core

/**
 * Fail-closed decoder boundary for persisted task checkpoints.
 *
 * Corrupt/stale serialized state can legitimately throw ordinary Exceptions while decoding and
 * should be treated as unusable recovery evidence. Fatal JVM/Android Errors are not corrupted
 * checkpoint data and must propagate instead of being disguised as a missing saved session.
 */
internal object CheckpointDecodeBoundary {
    fun <T> decodeOrNull(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}
