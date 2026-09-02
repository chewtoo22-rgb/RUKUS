package com.ruckus.agent.core

/**
 * Admission policy for exact Android package identifiers supplied through the command language.
 *
 * The parser should reject malformed identifiers before they reach PackageManager or execution.
 * This deliberately validates structure only; launchability is still checked by device preflight.
 */
object PackageIdentifierPolicy {
    private const val MAX_LENGTH = 255
    private val segment = Regex("^[A-Za-z][A-Za-z0-9_]*$")

    fun isValid(raw: String): Boolean {
        if (raw.isEmpty() || raw.length > MAX_LENGTH) return false
        if (raw.any { it.isWhitespace() || it.isISOControl() }) return false

        val parts = raw.split('.')
        if (parts.size < 2 || parts.any { it.isEmpty() }) return false
        return parts.all(segment::matches)
    }
}
