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
import com.ruckus.agent.personality.RuckusPersona

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF090909)) {
                    Dashboard(
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

@Composable
private fun Dashboard(onAccessibility: () -> Unit, onWriteSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(RuckusPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(RuckusPersona.TAGLINE, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        StatusCard("DEVICE AGENT", "Control layer scaffolded")
        StatusCard("APP FORGE", "Project planner scaffolded")
        StatusCard("GAME SHOP", "2D/3D project contract scaffolded")
        Spacer(Modifier.weight(1f))
        Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Enable control service") }
        OutlinedButton(onClick = onWriteSettings, modifier = Modifier.fillMaxWidth()) { Text("Grant settings control") }
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
