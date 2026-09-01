package com.ruckus.agent.builder

/**
 * Pure migration boundary for persisted builder project metadata while RUKUS transitions to NITRO.
 * This layer does not read files, execute builds, or call Android APIs.
 */
object NitroProjectStateMigration {
    const val CURRENT_SCHEMA = 2
    const val LEGACY_RUKUS_SCHEMA = 1
    const val PRODUCT_NITRO = "nitro"

    private const val MAX_NAME = 80
    private const val MAX_PACKAGE = 160
    private const val MAX_DESCRIPTION = 2000
    private const val MAX_FEATURES = 32
    private const val MAX_FEATURE_LENGTH = 120

    data class PersistedProjectState(
        val schemaVersion: Int,
        val product: String,
        val name: String,
        val packageName: String,
        val kind: String,
        val description: String,
        val features: List<String> = emptyList()
    )

    data class MigrationResult(
        val state: PersistedProjectState,
        val migrated: Boolean
    )

    fun migrate(input: PersistedProjectState): MigrationResult {
        require(input.schemaVersion in LEGACY_RUKUS_SCHEMA..CURRENT_SCHEMA) {
            "unsupported project schema ${input.schemaVersion}"
        }

        val normalized = normalize(input)
        return when (input.schemaVersion) {
            LEGACY_RUKUS_SCHEMA -> {
                require(input.product.equals("rukus", ignoreCase = true)) {
                    "schema v1 must originate from rukus"
                }
                MigrationResult(
                    state = normalized.copy(
                        schemaVersion = CURRENT_SCHEMA,
                        product = PRODUCT_NITRO
                    ),
                    migrated = true
                )
            }
            CURRENT_SCHEMA -> {
                require(input.product == PRODUCT_NITRO) {
                    "schema v2 must use nitro product identity"
                }
                MigrationResult(normalized, migrated = false)
            }
            else -> error("unreachable schema")
        }
    }

    private fun normalize(input: PersistedProjectState): PersistedProjectState {
        val name = input.name.trim()
        val packageName = input.packageName.trim()
        val description = input.description.trim()
        val kind = input.kind.trim().uppercase()
        val features = input.features.map { it.trim() }

        require(name.isNotEmpty() && name.length <= MAX_NAME && name.hasNoControlChars()) {
            "invalid project name"
        }
        require(packageName.length in 3..MAX_PACKAGE && PACKAGE_REGEX.matches(packageName)) {
            "invalid package name"
        }
        require(kind in BuildKind.entries.map { it.name }) { "invalid build kind" }
        require(description.length <= MAX_DESCRIPTION && description.hasNoControlChars()) {
            "invalid description"
        }
        require(features.size <= MAX_FEATURES) { "too many features" }
        require(features.all { it.isNotEmpty() && it.length <= MAX_FEATURE_LENGTH && it.hasNoControlChars() }) {
            "invalid feature"
        }
        require(features.distinct().size == features.size) { "duplicate features" }

        return input.copy(
            product = input.product.trim().lowercase(),
            name = name,
            packageName = packageName,
            kind = kind,
            description = description,
            features = features
        )
    }

    private fun String.hasNoControlChars(): Boolean = none { it.isISOControl() }

    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
}
