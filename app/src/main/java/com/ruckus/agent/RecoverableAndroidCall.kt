package com.ruckus.agent

/**
 * UI-facing replacement for Kotlin's catch-all runCatching.
 *
 * Android capability probes and permission requests may fail with recoverable RuntimeExceptions
 * (for example binder/provider/security failures). Those should degrade to the existing fallback
 * UI state. Fatal Error conditions must remain visible instead of being mislabeled as ordinary
 * permission/capability unavailability.
 *
 * This package-local function intentionally shadows Kotlin's default runCatching for the Android
 * activity layer, where the existing call sites consume Result via getOrNull/getOrDefault.
 */
internal inline fun <T> runCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: RuntimeException) {
        Result.failure(error)
    }
