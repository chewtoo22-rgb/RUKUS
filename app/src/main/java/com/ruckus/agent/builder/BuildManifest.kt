package com.ruckus.agent.builder

data class GeneratedFile(
    val path: String,
    val content: String
)

data class BuildManifest(
    val projectId: String,
    val files: List<GeneratedFile>,
    val tasks: List<String>
)

class ManifestGenerator {
    fun generate(record: ProjectRecord): BuildManifest {
        val spec = record.spec
        val safeName = spec.name.replace(Regex("[^A-Za-z0-9_]"), "") .ifBlank { "MutinyApp" }
        val packagePath = spec.packageName.replace('.', '/')
        val files = when (spec.kind) {
            BuildKind.ANDROID_APP -> androidFiles(safeName, packagePath, spec)
            BuildKind.GAME_2D -> gameFiles(safeName, packagePath, spec, false)
            BuildKind.GAME_3D -> gameFiles(safeName, packagePath, spec, true)
        }
        return BuildManifest(
            projectId = record.id,
            files = files,
            tasks = listOf("write_files", "static_check", "assemble_debug", "handoff_to_rukus")
        )
    }

    private fun androidFiles(name: String, packagePath: String, spec: ProjectSpec): List<GeneratedFile> = listOf(
        GeneratedFile("settings.gradle.kts", "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name = \"$name\"\ninclude(\":app\")"),
        GeneratedFile("app/src/main/java/$packagePath/MainActivity.kt", "package ${spec.packageName}\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\nimport androidx.compose.material3.Text\n\nclass MainActivity : ComponentActivity() {\n override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { Text(\"$name\") } }\n}")
    )

    private fun gameFiles(name: String, packagePath: String, spec: ProjectSpec, is3d: Boolean): List<GeneratedFile> = androidFiles(name, packagePath, spec) +
        GeneratedFile("MUTINY_GAME_PLAN.md", "# $name\n\nMode: ${if (is3d) "3D" else "2D"}\n\n${spec.description}\n\nFeatures:\n${spec.features.joinToString("\n") { "- $it" }}")
}
