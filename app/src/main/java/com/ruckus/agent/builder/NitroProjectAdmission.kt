package com.ruckus.agent.builder

private const val MAX_PROJECT_NAME = 80
private const val MAX_DESCRIPTION = 2_000
private const val MAX_FEATURES = 64
private const val MAX_FEATURE_LENGTH = 160
private val PACKAGE_SEGMENT = Regex("^[a-z][a-z0-9_]*$")

data class AdmittedProjectSpec(
    val name: String,
    val packageName: String,
    val kind: BuildKind,
    val description: String,
    val features: List<String>,
)

class ProjectAdmissionException(message: String) : IllegalArgumentException(message)

/**
 * Pure admission boundary for NITRO builder inputs.
 *
 * This layer performs no file, process, network, Android, or shell operations. It exists so
 * malformed or ambiguous project metadata is rejected before later materialization/build lanes.
 */
object NitroProjectAdmission {
    fun admit(spec: ProjectSpec): AdmittedProjectSpec {
        val name = normalizeHumanText(spec.name, "project name", MAX_PROJECT_NAME, allowEmpty = false)
        val description = normalizeHumanText(spec.description, "description", MAX_DESCRIPTION, allowEmpty = true)
        val packageName = validatePackageName(spec.packageName)

        if (spec.features.size > MAX_FEATURES) {
            throw ProjectAdmissionException("feature count exceeds $MAX_FEATURES")
        }

        val normalizedFeatures = spec.features.mapIndexed { index, feature ->
            normalizeHumanText(feature, "feature[$index]", MAX_FEATURE_LENGTH, allowEmpty = false)
        }

        val duplicates = normalizedFeatures
            .groupBy { it.lowercase() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            throw ProjectAdmissionException("duplicate feature declarations are not allowed")
        }

        return AdmittedProjectSpec(
            name = name,
            packageName = packageName,
            kind = spec.kind,
            description = description,
            features = normalizedFeatures,
        )
    }

    private fun validatePackageName(raw: String): String {
        if (raw != raw.trim()) throw ProjectAdmissionException("package name must not contain edge whitespace")
        if (raw.length !in 3..200) throw ProjectAdmissionException("package name length is invalid")
        if (raw.any { it.isISOControl() || it.isWhitespace() || it == '/' || it == '\\' || it == ';' || it == ':' }) {
            throw ProjectAdmissionException("package name contains forbidden characters")
        }

        val segments = raw.split('.')
        if (segments.size < 2 || segments.any { !PACKAGE_SEGMENT.matches(it) }) {
            throw ProjectAdmissionException("package name must be a lowercase dotted Android identifier")
        }
        return raw
    }

    private fun normalizeHumanText(
        raw: String,
        field: String,
        maxLength: Int,
        allowEmpty: Boolean,
    ): String {
        if (raw.any { it.isISOControl() }) throw ProjectAdmissionException("$field contains control characters")
        val normalized = raw.trim().replace(Regex("[ \\t]+"), " ")
        if (!allowEmpty && normalized.isEmpty()) throw ProjectAdmissionException("$field must not be empty")
        if (normalized.length > maxLength) throw ProjectAdmissionException("$field exceeds $maxLength characters")
        return normalized
    }
}
