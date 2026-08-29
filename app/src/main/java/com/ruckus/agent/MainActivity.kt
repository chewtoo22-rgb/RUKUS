package com.ruckus.agent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruckus.agent.control.RuckusAccessibilityService
import com.ruckus.agent.control.ShizukuStateReader
import com.ruckus.agent.core.AgentTaskState
import com.ruckus.agent.core.CompletedTaskEvidencePolicy
import com.ruckus.agent.core.DeviceReadyExecutor
import com.ruckus.agent.core.ExecutionReport
import com.ruckus.agent.personality.RuckusPersona
import com.ruckus.agent.ui.SystemTelemetry
import com.ruckus.agent.ui.SystemTelemetryReader
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

private val Ink = Color(0xFF070709)
private val Panel = Color(0xFF111116)
private val PanelRaised = Color(0xFF17171E)
private val Violet = Color(0xFF9A7BFF)
private val VioletSoft = Color(0xFF6E54C9)
private val TextPrimary = Color(0xFFF4F2F8)
private val TextMuted = Color(0xFF9C98A8)
private val Success = Color(0xFF69E6A6)
private val Warning = Color(0xFFFFC56A)
private val Danger = Color(0xFFFF7575)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val executor = DeviceReadyExecutor(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink,
                    surface = Panel,
                    primary = Violet,
                    onPrimary = Color.White,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    RukusCommandCenter(
                        executor = executor,
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

private enum class RukusPage { HOME, DEVICE, HISTORY, SETTINGS }

@Composable
private fun RukusCommandCenter(
    executor: DeviceReadyExecutor,
    onAccessibility: () -> Unit,
    onWriteSettings: () -> Unit
) {
    var page by remember { mutableStateOf(RukusPage.HOME) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Header(page)
        Box(Modifier.weight(1f)) {
            when (page) {
                RukusPage.HOME -> HomePage(executor)
                RukusPage.DEVICE -> DevicePage()
                RukusPage.HISTORY -> HistoryPage(executor)
                RukusPage.SETTINGS -> SettingsPage(onAccessibility, onWriteSettings)
            }
        }
        BottomNav(page) { page = it }
    }
}

@Composable
private fun Header(page: RukusPage) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(
                    Brush.radialGradient(listOf(Violet, Color(0xFF241735), Ink))
                ).border(1.dp, Violet.copy(alpha = .55f), CircleShape)
            )
            Column {
                Text("RUKUS", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                Text(page.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextMuted, fontSize = 12.sp)
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Success.copy(alpha = .10f)) {
            Text("SYSTEM ONLINE", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun HomePage(executor: DeviceReadyExecutor) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Ready for a command.") }
    var session by remember { mutableStateOf(executor.lastSession()) }
    var telemetry by remember { mutableStateOf(SystemTelemetryReader.read(context)) }
    var shizukuRefresh by remember { mutableIntStateOf(0) }
    val shizuku = remember(shizukuRefresh) { runCatching { ShizukuStateReader.read() }.getOrNull() }

    LaunchedEffect(Unit) {
        while (true) {
            telemetry = SystemTelemetryReader.read(context)
            delay(4000)
        }
    }

    fun show(report: ExecutionReport) {
        result = when {
            report.ok -> report.message
            report.needsConfirmation -> "Confirmation required: ${report.message}"
            else -> report.message
        }
        session = executor.lastSession()
    }
    fun execute(text: String) {
        if (text.isBlank()) return
        command = text
        show(executor.run(text))
    }
    fun resume(approved: Boolean = false) = show(executor.resumeLast(approved))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TelemetryStrip(telemetry)

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Panel,
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = .05f), RoundedCornerShape(28.dp))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Violet.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                        Text("R", color = Violet, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("What should I do?", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("Device control is live. Commands are grounded and verified.", color = TextMuted, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Open Spotify and play my playlist…", color = TextMuted) },
                    shape = RoundedCornerShape(20.dp),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = Color.White.copy(alpha = .10f),
                        focusedContainerColor = PanelRaised,
                        unfocusedContainerColor = PanelRaised
                    )
                )
                Button(
                    onClick = { execute(command) },
                    enabled = command.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("EXECUTE", fontWeight = FontWeight.Bold, letterSpacing = .8.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("Inspect") { execute("inspect screen") }
                    QuickAction("Scroll") { execute("scroll down") }
                    QuickAction("Home") { execute("home") }
                }
            }
        }

        ExecutionCard(result, session)

        if (session?.status == AgentTaskState.Status.RUNNING ||
            session?.status == AgentTaskState.Status.RECOVERING ||
            session?.status == AgentTaskState.Status.EXECUTING ||
            session?.status == AgentTaskState.Status.WAITING_CONFIRMATION
        ) {
            OutlinedButton(onClick = { resume(false) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text("RESUME SAVED TASK")
            }
        }
        if (session?.status == AgentTaskState.Status.WAITING_CONFIRMATION) {
            Button(onClick = { resume(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text("CONFIRM PENDING ACTION")
            }
        }

        PermissionHealthCard(
            accessibilityOnline = RuckusAccessibilityService.instance != null,
            shizukuOnline = shizuku?.binderAvailable == true,
            shizukuGranted = shizuku?.permissionGranted == true,
            onGrantShizuku = {
                runCatching { Shizuku.requestPermission(1001) }
                shizukuRefresh++
            }
        )
    }
}

@Composable
private fun TelemetryStrip(t: SystemTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DEVICE PULSE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("BATTERY", "${t.batteryPercent}%", if (t.batteryCharging) "charging" else "on battery", Modifier.weight(1f))
            Metric("RAM", "${t.ramUsedPercent}%", "${"%.1f".format(t.ramUsedGb)}/${"%.1f".format(t.ramTotalGb)} GB", Modifier.weight(1f))
            Metric("CPU", t.cpuLoadPercent?.let { "$it%" } ?: "—", "1m load", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("TEMP", t.batteryTempC?.let { "${"%.1f".format(it)}°C" } ?: "—", t.thermalStatus, Modifier.weight(1f))
            Metric("STORAGE", "${t.storageUsedPercent}%", "${"%.1f".format(t.storageFreeGb)} GB free", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = PanelRaised) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun QuickAction(label: String, action: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = action),
        shape = RoundedCornerShape(14.dp),
        color = Violet.copy(alpha = .10f)
    ) { Text(label, color = Violet, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) }
}

@Composable
private fun ExecutionCard(result: String, session: AgentTaskState?) {
    val statusColor = when (session?.status) {
        AgentTaskState.Status.COMPLETE -> Success
        AgentTaskState.Status.FAILED -> Danger
        AgentTaskState.Status.WAITING_CONFIRMATION -> Warning
        else -> Violet
    }
    Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(8.dp))
                Text("EXECUTION", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Spacer(Modifier.weight(1f))
                Text(session?.status?.name ?: "READY", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(result, fontSize = 14.sp)
            session?.let { saved ->
                HorizontalDivider(color = Color.White.copy(alpha = .06f))
                ExecutionStep("UNDERSTAND", true)
                ExecutionStep("PLAN", saved.totalSteps > 0)
                ExecutionStep("EXECUTE", saved.currentStep > 0)
                ExecutionStep("VERIFY", saved.status == AgentTaskState.Status.COMPLETE && CompletedTaskEvidencePolicy.isStillValid(saved))
                Text("Step ${saved.currentStep}/${saved.totalSteps}  •  Recoveries ${saved.recoveryAttempts}", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ExecutionStep(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (done) Success else Color.White.copy(alpha = .12f)))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (done) TextPrimary else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermissionHealthCard(
    accessibilityOnline: Boolean,
    shizukuOnline: Boolean,
    shizukuGranted: Boolean,
    onGrantShizuku: () -> Unit
) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CONTROL HEALTH", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            HealthRow("Accessibility", accessibilityOnline)
            HealthRow("Shizuku service", shizukuOnline)
            HealthRow("Shizuku permission", shizukuGranted)
            if (shizukuOnline && !shizukuGranted) {
                TextButton(onClick = onGrantShizuku) { Text("GRANT SHIZUKU") }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) Success else Danger))
        Spacer(Modifier.width(9.dp))
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Text(if (ok) "ONLINE" else "OFFLINE", color = if (ok) Success else Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DevicePage() {
    val context = LocalContext.current
    var telemetry by remember { mutableStateOf(SystemTelemetryReader.read(context)) }
    LaunchedEffect(Unit) {
        while (true) { telemetry = SystemTelemetryReader.read(context); delay(4000) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Device Intelligence", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Live health and resource telemetry from this device.", color = TextMuted)
        TelemetryStrip(telemetry)
        Surface(shape = RoundedCornerShape(28.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(150.dp).height(290.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF19131F), Color(0xFF0A090D))))
                        .border(1.dp, Violet.copy(alpha = .35f), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RUKUS", color = Violet, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("DEVICE LINK", color = TextMuted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Thermal state: ${telemetry.thermalStatus}", color = if (telemetry.thermalStatus in listOf("Hot", "Critical", "Emergency")) Warning else Success)
            }
        }
    }
}

@Composable
private fun HistoryPage(executor: DeviceReadyExecutor) {
    val session = remember { executor.lastSession() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Task History", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Current persisted task checkpoint and completion evidence.", color = TextMuted)
        if (session == null) {
            Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
                Text("No persisted task yet.", color = TextMuted, modifier = Modifier.padding(20.dp))
            }
        } else {
            Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.request, fontWeight = FontWeight.SemiBold)
                    Text("${session.status.name} • step ${session.currentStep}/${session.totalSteps}", color = Violet)
                    Text(session.lastScreenSummary?.takeIf { it.isNotBlank() } ?: "No screen observation recorded.", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onAccessibility: () -> Unit, onWriteSettings: () -> Unit) {
    var telemetryEnabled by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var verboseEvidence by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Control RUKUS presentation and Android integration points.", color = TextMuted)
        SettingsGroup("INTERFACE") {
            SettingToggle("Live telemetry", "Show device health on the home screen", telemetryEnabled) { telemetryEnabled = it }
            SettingToggle("Haptic feedback", "Use subtle feedback for command actions", haptics) { haptics = it }
            SettingToggle("Verbose evidence", "Show deeper completion and verification details", verboseEvidence) { verboseEvidence = it }
        }
        SettingsGroup("ANDROID CONTROL") {
            SettingAction("Accessibility service", "Required for screen observation and semantic control", onAccessibility)
            SettingAction("Modify system settings", "Manage Android write-settings permission", onWriteSettings)
        }
        SettingsGroup("ABOUT") {
            Text(RuckusPersona.NAME, fontWeight = FontWeight.Bold)
            Text(RuckusPersona.TAGLINE, color = TextMuted, fontSize = 12.sp)
            Text("Executor V1 command engine", color = Violet, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = TextMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingAction(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = TextMuted, fontSize = 11.sp)
        }
        Text("OPEN", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomNav(current: RukusPage, onSelect: (RukusPage) -> Unit) {
    Surface(color = Color(0xFF0B0B0E), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            NavItem("Home", RukusPage.HOME, current, onSelect, Modifier.weight(1f))
            NavItem("Device", RukusPage.DEVICE, current, onSelect, Modifier.weight(1f))
            NavItem("History", RukusPage.HISTORY, current, onSelect, Modifier.weight(1f))
            NavItem("Settings", RukusPage.SETTINGS, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(label: String, page: RukusPage, current: RukusPage, onSelect: (RukusPage) -> Unit, modifier: Modifier) {
    val selected = page == current
    Column(
        modifier.clickable { onSelect(page) }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.width(if (selected) 26.dp else 6.dp).height(3.dp).clip(CircleShape).background(if (selected) Violet else Color.Transparent))
        Text(label, color = if (selected) TextPrimary else TextMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
