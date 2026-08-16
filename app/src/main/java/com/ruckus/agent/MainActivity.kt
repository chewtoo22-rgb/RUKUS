package com.ruckus.agent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ruckus.agent.builder.*
import com.ruckus.agent.control.DeviceController
import com.ruckus.agent.core.CommandProtocol
import com.ruckus.agent.core.RukusCommandRouter
import com.ruckus.agent.personality.MutinyPersona
import com.ruckus.agent.personality.RuckusPersona

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF090909)) {
                    DualAgentShell(
                        onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onWriteSettings = {
                            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                        }
                    )
                }
            }
        }
    }
}

private enum class Workspace { RUKUS, MUTINY }

@Composable
private fun DualAgentShell(onAccessibility: () -> Unit, onWriteSettings: () -> Unit) {
    var workspace by remember { mutableStateOf(Workspace.RUKUS) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(workspace == Workspace.RUKUS, { workspace = Workspace.RUKUS }, { Text("R") }, label = { Text("RUKUS") })
                NavigationBarItem(workspace == Workspace.MUTINY, { workspace = Workspace.MUTINY }, { Text("M") }, label = { Text("MUTINY") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (workspace) {
                Workspace.RUKUS -> RukusWorkspace(onAccessibility, onWriteSettings)
                Workspace.MUTINY -> MutinyWorkspace()
            }
        }
    }
}

@Composable
private fun RukusWorkspace(onAccessibility: () -> Unit, onWriteSettings: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { DeviceController(context) }
    var commandText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Command channel ready.") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(RuckusPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(RuckusPersona.TAGLINE, style = MaterialTheme.typography.titleMedium)
        Text("PHONE CONTROL", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()

        OutlinedTextField(
            value = commandText,
            onValueChange = { commandText = it },
            label = { Text("Tell RUKUS what to do") },
            supportingText = { Text("Examples: read screen · click Bluetooth · type hello · brightness 40 · volume 25") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val command = CommandProtocol.parseRukus(commandText)
                val reply = RukusCommandRouter.route(command)
                if (reply.actions.isEmpty()) {
                    resultText = reply.message
                } else {
                    resultText = reply.actions.joinToString("\n") { action ->
                        controller.execute(action).fold(
                            onSuccess = { "✓ $it" },
                            onFailure = { "✗ ${it.message ?: it::class.java.simpleName}" }
                        )
                    }
                }
            },
            enabled = commandText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("RUKUS, execute") }

        Card(Modifier.fillMaxWidth()) {
            Text(resultText, Modifier.padding(16.dp))
        }
        StatusCard("INTELLIGENCE LOOP", "Natural language → intent → typed action → Android controller")
        StatusCard("SCREEN SENSE", "Read visible accessibility text and UI structure")
        StatusCard("SEMANTIC CONTROL", "Click by label, type into focused fields, tap and swipe")
        Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Enable control service") }
        OutlinedButton(onClick = onWriteSettings, modifier = Modifier.fillMaxWidth()) { Text("Grant settings control") }
    }
}

@Composable
private fun MutinyWorkspace() {
    val context = LocalContext.current
    val builder = remember { BuilderEngine() }
    val store = remember { ProjectStore(context) }
    val generator = remember { ManifestGenerator() }
    var projectName by remember { mutableStateOf("Untitled") }
    var description by remember { mutableStateOf("") }
    var featuresText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(BuildKind.ANDROID_APP) }
    var plan by remember { mutableStateOf<List<String>>(emptyList()) }
    var manifestSummary by remember { mutableStateOf("No manifest generated yet.") }
    var projects by remember { mutableStateOf(store.all()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(MutinyPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(MutinyPersona.TAGLINE, style = MaterialTheme.typography.titleMedium)
        Text("APP + GAME WORKSHOP", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()

        OutlinedTextField(projectName, { projectName = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("What are we building?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(featuresText, { featuresText = it }, label = { Text("Features, one per line") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == BuildKind.ANDROID_APP, { kind = BuildKind.ANDROID_APP }, label = { Text("App") })
            FilterChip(kind == BuildKind.GAME_2D, { kind = BuildKind.GAME_2D }, label = { Text("2D Game") })
            FilterChip(kind == BuildKind.GAME_3D, { kind = BuildKind.GAME_3D }, label = { Text("3D Game") })
        }

        Button(
            onClick = {
                val safe = projectName.lowercase().replace(Regex("[^a-z0-9]+"), "").ifBlank { "untitled" }
                val spec = ProjectSpec(
                    name = projectName,
                    packageName = "com.mutiny.$safe",
                    kind = kind,
                    description = description,
                    features = featuresText.lines().map { it.trim() }.filter { it.isNotBlank() }
                )
                val record = store.create(spec)
                plan = builder.plan(spec)
                val manifest = generator.generate(record)
                manifestSummary = "${manifest.files.size} generated files · ${manifest.tasks.size} pipeline tasks · project ${record.id.take(8)}"
                projects = store.all()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("MUTINY, create project") }

        if (plan.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BUILD QUEUE", style = MaterialTheme.typography.titleMedium)
                    plan.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    HorizontalDivider()
                    Text(manifestSummary)
                }
            }
        }

        Text("PROJECT VAULT", style = MaterialTheme.typography.titleMedium)
        if (projects.isEmpty()) {
            StatusCard("EMPTY SHOP", "Create the first persistent MUTINY project above.")
        } else {
            projects.take(5).forEach { project ->
                StatusCard(project.spec.name, "${project.spec.kind} · ${project.status} · ${project.id.take(8)}")
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(detail)
        }
    }
}
