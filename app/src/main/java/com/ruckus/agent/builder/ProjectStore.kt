package com.ruckus.agent.builder

import android.content.Context
import java.util.UUID

class ProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("mutiny_projects", Context.MODE_PRIVATE)

    fun create(spec: ProjectSpec): ProjectRecord {
        val now = System.currentTimeMillis()
        val record = ProjectRecord(
            id = UUID.randomUUID().toString(),
            spec = spec,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        save(record)
        return record
    }

    fun save(record: ProjectRecord) {
        prefs.edit().putString(record.id, encode(record)).apply()
    }

    fun get(id: String): ProjectRecord? = prefs.getString(id, null)?.let(::decode)

    fun all(): List<ProjectRecord> = prefs.all.values
        .mapNotNull { (it as? String)?.let(::decode) }
        .sortedByDescending { it.updatedAtEpochMs }

    private fun encode(record: ProjectRecord): String = listOf(
        record.id,
        record.spec.name,
        record.spec.packageName,
        record.spec.kind.name,
        record.spec.description,
        record.spec.features.joinToString("\u001F"),
        record.createdAtEpochMs.toString(),
        record.updatedAtEpochMs.toString(),
        record.status.name
    ).joinToString("\u001E") { it.replace("\u001E", " ") }

    private fun decode(raw: String): ProjectRecord? = runCatching {
        val p = raw.split("\u001E")
        ProjectRecord(
            id = p[0],
            spec = ProjectSpec(
                name = p[1],
                packageName = p[2],
                kind = BuildKind.valueOf(p[3]),
                description = p[4],
                features = p[5].split("\u001F").filter { it.isNotBlank() }
            ),
            createdAtEpochMs = p[6].toLong(),
            updatedAtEpochMs = p[7].toLong(),
            status = ProjectStatus.valueOf(p[8])
        )
    }.getOrNull()
}
