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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ruckus.agent.control.RuckusAccessibilityService
import com.ruckus.agent.control.ShizukuStateReader
import com.ruckus.agent.core.AgentTaskState
import com.ruckus.agent.core.CompletedTaskEvidencePolicy
import com.ruckus.agent.core.DeviceReadyExecutor
import com.ruckus.agent.core.ExecutionReport
import com.ruckus.agent.personality.RuckusPersona
import com.ruckus.agent.settings.OnboardingReadiness
import com.ruckus.agent.settings.OnboardingReadinessPolicy
import com.ruckus.agent.settings.OnboardingStep
import com.ruckus.agent.settings.RukusSettingsController
import com.ruckus.agent.settings.SharedPreferencesRukusSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {
    private var capabilityRefreshGeneration by mutableIntStateOf(0)
    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            capabilityRefreshGeneration++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        val executor = DeviceReadyExecutor(this)
        val settingsController = RukusSettingsController(SharedPreferencesRukusSettingsStore(this))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF090909)) {
                    RukusRoot(
                        executor = executor,
                        settingsController = settingsController,
                        capabilityRefreshGeneration = capabilityRefreshGeneration,
                        canWriteSettings = { Settings.System.canWrite(this) },
                        onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onWriteSettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        capabilityRefreshGeneration++
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        super.onDestroy()
    }
}

@Composable
private fun RukusRoot(
    executor: DeviceReadyExecutor,
    settingsController: RukusSettingsController,
    capabilityRefreshGeneration: Int,
    canWriteSettings: () -> Boolean,
    onAccessibility: () -> Unit,
    onWriteSettings: () -> Unit
) {
    var onboardingComplete by remember { mutableStateOf(!settingsController.needsOnboarding()) }

    if (!onboardingComplete) {
        OnboardingGate(
            settingsController = settingsController,
            capabilityRefreshGeneration = capabilityRefreshGeneration,
            canWriteSettings = canWriteSettings,
            onAccessibility = onAccessibility,
            onWriteSettings = onWriteSettings,
            onCompleted = { onboardingComplete = true }
        )
    } else {
        Dashboard(
            executor = executor,
            settingsController = settingsController,
            capabilityRefreshGeneration = capabilityRefreshGeneration,
            onAccessibility = onAccessibility,
            onWriteSettings = onWriteSettings
        )
    }
}

@Composable
private fun OnboardingGate(
    settingsController: RukusSettingsController,
    capabilityRefreshGeneration: Int,
    canWriteSettings: () -> Boolean,
    onAccessibility: () -> Unit,
    onWriteSettings: () -> Unit,
    onCompleted: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    var safetyAcknowledged by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Complete the required setup before RUKUS can execute commands.") }

    val accessibilityReady = remember(refresh, capabilityRefreshGeneration) { RuckusAccessibilityService.instance != null }
    val writeSettingsReady = remember(refresh, capabilityRefreshGeneration) { runCatching { canWriteSettings() }.getOrDefault(false) }
    val shizuku = remember(refresh, capabilityRefreshGeneration) { runCatching { ShizukuStateReader.read() }.getOrNull() }
    val shizukuReady = shizuku?.permissionGranted == true
    val readiness = OnboardingReadiness(
        accessibilityReady = accessibilityReady,
        writeSettingsReady = writeSettingsReady,
        shizukuReady = shizukuReady,
        safetyAcknowledged = safetyAcknowledged
    )
    val plan = OnboardingReadinessPolicy.evaluate(readiness)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("RUKUS SETUP", style = MaterialTheme.typography.displaySmall)
        Text("First-run readiness gate")
        HorizontalDivider()
        Text(message, style = MaterialTheme.typography.bodyMedium)

        SetupStatus(
            title = "Accessibility service",
            ready = accessibilityReady,
            required = true,
            detail = if (accessibilityReady) "Ready" else "Required for screen observation and controlled interaction"
        )
        OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text(if (accessibilityReady) "Review Accessibility" else "Enable Accessibility")
        }

        SetupStatus(
            title = "Modify system settings",
            ready = writeSettingsReady,
            required = false,
            detail = if (writeSettingsReady) "Ready" else "Optional now; required by commands that change protected settings"
        )
        OutlinedButton(onClick = onWriteSettings, modifier = Modifier.fillMaxWidth()) {
            Text(if (writeSettingsReady) "Review system-settings access" else "Grant system-settings access")
        }

        SetupStatus(
            title = "Shizuku",
            ready = shizukuReady,
            required = false,
            detail = when {
                shizukuReady -> "Connected and permission granted"
                shizuku?.binderAvailable == true -> "Connected; optional permission not granted"
                else -> "Optional; service is not currently available"
            }
        )
        if (shizuku?.binderAvailable == true && !shizukuReady) {
            OutlinedButton(
                onClick = {
                    runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE) }
                    refresh++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Shizuku permission")
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = safetyAcknowledged, onCheckedChange = { safetyAcknowledged = it })
            Text(
                "I understand RUKUS can operate Android on my behalf and that sensitive actions may require confirmation.",
                modifier = Modifier.weight(1f)
            )
        }

        val missingRequired = plan.requiredBlockers.joinToString { blocker ->
            when (blocker) {
                OnboardingStep.INTRO -> "intro"
                OnboardingStep.ACCESSIBILITY -> "Accessibility"
                OnboardingStep.SAFETY -> "safety acknowledgement"
                else -> blocker.name.lowercase()
            }
        }
        if (missingRequired.isNotBlank()) {
            Text("Required before continuing: $missingRequired", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                val completionPlan = settingsController.completeOnboardingIfReady(readiness)
                if (completionPlan.canComplete) {
                    onCompleted()
                } else {
                    message = if (OnboardingStep.PERSISTENCE in completionPlan.requiredBlockers) {
                        "Setup is ready, but RUKUS could not save onboarding state. Check app storage and try again."
                    } else {
                        "Setup is not complete. Required capabilities still need attention."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = plan.canComplete
        ) {
            Text("Finish setup")
        }
        TextButton(
            onClick = { refresh++ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh setup status")
        }
        if (plan.optionalSetup.isNotEmpty()) {
            Text(
                "Optional setup can be completed later. Commands that need those capabilities will still fail closed until granted.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SetupStatus(title: String, ready: Boolean, required: Boolean, detail: String) {
    StatusCard(
        title = if (required) "$title • REQUIRED" else "$title • OPTIONAL",
        detail = "${if (ready) "READY" else "NOT READY"}\n$detail"
    )
}

@Composable
private fun Dashboard(
    executor: DeviceReadyExecutor,
    settingsController: RukusSettingsController,
    capabilityRefreshGeneration: Int,
    onAccessibility: () -> Unit,
    onWriteSettings: () -> Unit
) {
    var command by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Ready. Try: open Spotify, scroll down, inspect screen, tap Allow, volume 30") }
    var session by remember { mutableStateOf(executor.lastSession()) }
    var refresh by remember { mutableIntStateOf(0) }
    var isExecuting by remember { mutableStateOf(false) }
    var appSettings by remember { mutableStateOf(settingsController.snapshot()) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    val executionScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shizuku = remember(refresh, capabilityRefreshGeneration) { runCatching { ShizukuStateReader.read() }.getOrNull() }

    fun show(report: ExecutionReport) {
        result = if (report.ok) "✓ ${report.message}" else if (report.needsConfirmation) "CONFIRM: ${report.message}" else "✕ ${report.message}"
        session = executor.lastSession()
        if (appSettings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun launchExecution(block: () -> ExecutionReport) {
        if (isExecuting) return
        isExecuting = true
        executionScope.launch {
            try {
                val report = ExecutionUiBoundary.run {
                    withContext(Dispatchers.IO) { block() }
                }
                show(report)
            } finally {
                isExecuting = false
            }
        }
    }

    fun execute(text: String) {
        command = text
        launchExecution { executor.run(text) }
    }

    fun resume(approved: Boolean = false) {
        launchExecution { executor.resumeLast(approved) }
    }

    fun updateSetting(label: String, requested: Boolean, apply: () -> com.ruckus.agent.settings.RukusSettings, value: (com.ruckus.agent.settings.RukusSettings) -> Boolean) {
        val next = apply()
        appSettings = next
        settingsMessage = if (value(next) == requested) {
            "$label ${if (requested) "enabled" else "disabled"}."
        } else {
            "Could not save $label setting. Previous value was preserved."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(RuckusPersona.NAME, style = MaterialTheme.typography.displayMedium)
        Text(RuckusPersona.TAGLINE)
        HorizontalDivider()
        StatusCard("CONTROL SERVICE", if (RuckusAccessibilityService.instance != null) "ONLINE" else "OFFLINE — enable Accessibility")
        StatusCard(
            "SHIZUKU",
            when {
                shizuku?.permissionGranted == true -> "CONNECTED + GRANTED"
                shizuku?.binderAvailable == true -> "CONNECTED — permission needed"
                else -> "OFFLINE"
            }
        )

        OutlinedTextField(
            command,
            { command = it },
            Modifier.fillMaxWidth(),
            label = { Text("Tell RUKUS what to do") },
            singleLine = true,
            enabled = !isExecuting
        )
        Button(
            onClick = { execute(command) },
            modifier = Modifier.fillMaxWidth(),
            enabled = command.isNotBlank() && !isExecuting
        ) {
            Text(if (isExecuting) "EXECUTING…" else "EXECUTE")
        }
        if (isExecuting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { execute("inspect screen") }, label = { Text("Inspect") }, enabled = !isExecuting)
            AssistChip(onClick = { execute("scroll down") }, label = { Text("Scroll ↓") }, enabled = !isExecuting)
            AssistChip(onClick = { execute("home") }, label = { Text("Home") }, enabled = !isExecuting)
        }
        Text(result, style = MaterialTheme.typography.bodyMedium)

        session?.let { saved ->
            StatusCard(
                "TASK CHECKPOINT",
                "${saved.status.name} • step ${saved.currentStep}/${saved.totalSteps} • recoveries ${saved.recoveryAttempts}"
            )
            val resumable = saved.status == AgentTaskState.Status.RUNNING ||
                saved.status == AgentTaskState.Status.RECOVERING ||
                saved.status == AgentTaskState.Status.EXECUTING ||
                saved.status == AgentTaskState.Status.WAITING_CONFIRMATION
            if (resumable) {
                OutlinedButton(
                    onClick = { resume(false) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExecuting
                ) { Text("RESUME SAVED TASK") }
            }
            if (saved.status == AgentTaskState.Status.WAITING_CONFIRMATION) {
                Button(
                    onClick = { resume(true) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExecuting
                ) {
                    Text("CONFIRM EXACT PENDING ACTION")
                }
                Text(
                    "Approval is accepted only for the fresh, action-bound checkpoint currently awaiting confirmation.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (saved.status == AgentTaskState.Status.COMPLETE || saved.status == AgentTaskState.Status.FAILED) {
                val proofStatus = when {
                    saved.status == AgentTaskState.Status.FAILED -> "FAILED / NOT PROVEN"
                    CompletedTaskEvidencePolicy.isStillValid(saved) -> "VERIFIED COMPLETE"
                    else -> "COMPLETION EVIDENCE STALE / NOT PROVEN"
                }
                val action = saved.lastAction?.toString()?.take(180) ?: "none"
                val observation = saved.lastScreenSummary?.takeIf { it.isNotBlank() }?.take(500) ?: "no final screen observation"
                StatusCard(
                    "TASK EVIDENCE",
                    "$proofStatus\nGoal: ${saved.request.take(240)}\nProgress: ${saved.currentStep}/${saved.totalSteps} • recoveries ${saved.recoveryAttempts}\nLast action: $action\nFinal observation: $observation"
                )
            }
        }

        HorizontalDivider()
        Text("RUKUS SETTINGS", style = MaterialTheme.typography.titleLarge)
        SettingToggle(
            title = "Execution telemetry",
            detail = "Collect bounded local execution-health events for reliability diagnostics.",
            checked = appSettings.telemetryEnabled,
            enabled = !isExecuting,
            onCheckedChange = { requested ->
                updateSetting("Telemetry", requested, { settingsController.setTelemetryEnabled(requested) }) { it.telemetryEnabled }
            }
        )
        SettingToggle(
            title = "Sensitive-action confirmations",
            detail = "Require confirmation prompts when the runtime policy marks an action as sensitive.",
            checked = appSettings.confirmationsEnabled,
            enabled = !isExecuting,
            onCheckedChange = { requested ->
                updateSetting("Confirmations", requested, { settingsController.setConfirmationsEnabled(requested) }) { it.confirmationsEnabled }
            }
        )
        SettingToggle(
            title = "Haptic feedback",
            detail = "Provide tactile feedback when an execution attempt completes.",
            checked = appSettings.hapticsEnabled,
            enabled = !isExecuting,
            onCheckedChange = { requested ->
                updateSetting("Haptics", requested, { settingsController.setHapticsEnabled(requested) }) { it.hapticsEnabled }
            }
        )
        settingsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onAccessibility, Modifier.weight(1f), enabled = !isExecuting) { Text("Accessibility") }
            OutlinedButton(onWriteSettings, Modifier.weight(1f), enabled = !isExecuting) { Text("System access") }
        }
        if (shizuku?.binderAvailable == true && shizuku.permissionGranted.not()) {
            OutlinedButton(
                onClick = {
                    runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE) }
                    refresh++
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExecuting
            ) { Text("Grant Shizuku") }
        }
        TextButton(
            onClick = {
                refresh++
                session = executor.lastSession()
                appSettings = settingsController.snapshot()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExecuting
        ) { Text("Refresh status") }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail)
        }
    }
}
