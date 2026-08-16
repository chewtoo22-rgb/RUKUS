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
        val safeName = spec.name.replace(Regex("[^A-Za-z0-9_]"), "").ifBlank { "MutinyApp" }
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
        GeneratedFile(
            "settings.gradle.kts",
            "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n" +
                "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\n" +
                "rootProject.name = \"$name\"\ninclude(\":app\")"
        ),
        GeneratedFile(
            "build.gradle.kts",
            "plugins {\n" +
                "    id(\"com.android.application\") version \"8.7.3\" apply false\n" +
                "    id(\"org.jetbrains.kotlin.android\") version \"2.1.0\" apply false\n" +
                "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.0\" apply false\n" +
                "}"
        ),
        GeneratedFile(
            "app/build.gradle.kts",
            "plugins {\n" +
                "    id(\"com.android.application\")\n" +
                "    id(\"org.jetbrains.kotlin.android\")\n" +
                "    id(\"org.jetbrains.kotlin.plugin.compose\")\n" +
                "}\n\n" +
                "android {\n" +
                "    namespace = \"${spec.packageName}\"\n" +
                "    compileSdk = 35\n" +
                "    defaultConfig { applicationId = \"${spec.packageName}\"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = \"0.1.0\" }\n" +
                "    buildFeatures { compose = true }\n" +
                "    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }\n" +
                "    kotlinOptions { jvmTarget = \"17\" }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "    implementation(platform(\"androidx.compose:compose-bom:2025.01.00\"))\n" +
                "    implementation(\"androidx.activity:activity-compose:1.10.0\")\n" +
                "    implementation(\"androidx.compose.material3:material3\")\n" +
                "}"
        ),
        GeneratedFile(
            "app/src/main/AndroidManifest.xml",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "  <application android:theme=\"@style/AppTheme\" android:label=\"$name\">\n" +
                "    <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                "      <intent-filter>\n" +
                "        <action android:name=\"android.intent.action.MAIN\"/>\n" +
                "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                "      </intent-filter>\n" +
                "    </activity>\n" +
                "  </application>\n" +
                "</manifest>"
        ),
        GeneratedFile(
            "app/src/main/res/values/styles.xml",
            "<resources><style name=\"AppTheme\" parent=\"android:style/Theme.Material.NoActionBar\" /></resources>"
        ),
        GeneratedFile(
            "app/src/main/java/$packagePath/MainActivity.kt",
            "package ${spec.packageName}\n\n" +
                "import android.os.Bundle\n" +
                "import androidx.activity.ComponentActivity\n" +
                "import androidx.activity.compose.setContent\n" +
                "import androidx.compose.material3.Text\n\n" +
                "class MainActivity : ComponentActivity() {\n" +
                " override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { Text(\"$name\") } }\n" +
                "}"
        )
    )

    private fun gameFiles(name: String, packagePath: String, spec: ProjectSpec, is3d: Boolean): List<GeneratedFile> =
        androidFiles(name, packagePath, spec) + GeneratedFile(
            "MUTINY_GAME_PLAN.md",
            "# $name\n\nMode: ${if (is3d) "3D" else "2D"}\n\n${spec.description}\n\nFeatures:\n${spec.features.joinToString("\n") { "- $it" }}"
        )
}
