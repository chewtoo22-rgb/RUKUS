package com.ruckus.agent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ruckus.agent.builder.BuildKind
import com.ruckus.agent.builder.BuilderEngine
import com.ruckus.agent.builder.ProjectSpec
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
                NavigationBarItem(
                    selected = workspace == Workspace.RUKUS,
                    onClick = { workspace = Workspace.RUKUS },
                    icon = { Text("R") },
                    label = { Text("RUKUS") }
                )
                NavigationBarItem(
                    selected = workspace == Workspace.MUTINY,
                    onClick = { workspace = Workspace.MUTINY },
                    icon = { Text("M") },
                    label = { Text("MUTINY") }
                )
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
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(RuckusPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(RuckusPersona.TAGLINE, style = MaterialTheme.typography.titleMedium)
        Text("PHONE CONTROL", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()
        StatusCard("SCREEN SENSE", "Read visible accessibility text and UI structure")
        StatusCard("SEMANTIC CONTROL", "Click by label, type into focused fields, tap and swipe")
        StatusCard("DEVICE TOOLS", "Apps, brightness, media, Home/Back, bounded Shizuku")
        Spacer(Modifier.weight(1f))
        Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Enable control service") }
        OutlinedButton(onClick = onWriteSettings, modifier = Modifier.fillMaxWidth()) { Text("Grant settings control") }
    }
}

@Composable
private fun MutinyWorkspace() {
    var projectName by remember { mutableStateOf("Untitled") }
    var description by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(BuildKind.ANDROID_APP) }
    var plan by remember { mutableStateOf<List<String>>(emptyList()) }
    val builder = remember { BuilderEngine() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(MutinyPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(MutinyPersona.TAGLINE, style = MaterialTheme.typography.titleMedium)
        Text("APP + GAME WORKSHOP", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()

        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            label = { Text("Project name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("What are we building?") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == BuildKind.ANDROID_APP, onClick = { kind = BuildKind.ANDROID_APP }, label = { Text("App") })
            FilterChip(selected = kind == BuildKind.GAME_2D, onClick = { kind = BuildKind.GAME_2D }, label = { Text("2D Game") })
            FilterChip(selected = kind == BuildKind.GAME_3D, onClick = { kind = BuildKind.GAME_3D }, label = { Text("3D Game") })
        }

        Button(
            onClick = {
                val safe = projectName.lowercase().replace(Regex("[^a-z0-9]+"), "").ifBlank { "untitled" }
                plan = builder.plan(
                    ProjectSpec(
                        name = projectName,
                        packageName = "com.mutiny.$safe",
                        kind = kind,
                        description = description
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("MUTINY, build the plan") }

        if (plan.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BUILD QUEUE", style = MaterialTheme.typography.titleMedium)
                    plan.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }
            }
        } else {
            StatusCard("WORKSHOP READY", "Separate workspace now; shared engine underneath")
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
