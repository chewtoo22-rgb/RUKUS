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
import androidx.compose.ui.unit.dp
import com.ruckus.agent.control.RuckusAccessibilityService
import com.ruckus.agent.control.ShizukuStateReader
import com.ruckus.agent.core.AgentTaskState
import com.ruckus.agent.core.DeviceReadyExecutor
import com.ruckus.agent.core.ExecutionReport
import com.ruckus.agent.personality.RuckusPersona
import rikka.shizuku.Shizuku

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val executor=DeviceReadyExecutor(this)
  setContent{MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize(),color=Color(0xFF090909)){Dashboard(executor,{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},{startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:$packageName")))})}}}
 }
}

@Composable private fun Dashboard(executor:DeviceReadyExecutor,onAccessibility:()->Unit,onWriteSettings:()->Unit){
 var command by remember{mutableStateOf("")}
 var result by remember{mutableStateOf("Ready. Try: open Spotify, scroll down, inspect screen, tap Allow, volume 30")}
 var session by remember{mutableStateOf(executor.lastSession())}
 var refresh by remember{mutableIntStateOf(0)}
 val shizuku=remember(refresh){runCatching{ShizukuStateReader.read()}.getOrNull()}
 fun show(report:ExecutionReport){
  result=if(report.ok)"✓ ${report.message}" else if(report.needsConfirmation)"CONFIRM: ${report.message}" else "✕ ${report.message}"
  session=executor.lastSession()
 }
 fun execute(text:String){command=text;show(executor.run(text))}
 fun resume(approved:Boolean=false){show(executor.resumeLast(approved))}

 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(RuckusPersona.NAME,style=MaterialTheme.typography.displayMedium);Text(RuckusPersona.TAGLINE);HorizontalDivider()
  StatusCard("CONTROL SERVICE",if(RuckusAccessibilityService.instance!=null)"ONLINE" else "OFFLINE — enable Accessibility")
  StatusCard("SHIZUKU",when{shizuku?.permissionGranted==true->"CONNECTED + GRANTED";shizuku?.binderAvailable==true->"CONNECTED — permission needed";else->"OFFLINE"})

  OutlinedTextField(command,{command=it},Modifier.fillMaxWidth(),label={Text("Tell RUKUS what to do")},singleLine=true)
  Button(onClick={execute(command)},modifier=Modifier.fillMaxWidth(),enabled=command.isNotBlank()){Text("EXECUTE")}

  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   AssistChip(onClick={execute("inspect screen")},label={Text("Inspect")})
   AssistChip(onClick={execute("scroll down")},label={Text("Scroll ↓")})
   AssistChip(onClick={execute("home")},label={Text("Home")})
  }
  Text(result,style=MaterialTheme.typography.bodyMedium)

  session?.let { saved ->
   StatusCard(
    "TASK CHECKPOINT",
    "${saved.status.name} • step ${saved.currentStep}/${saved.totalSteps} • recoveries ${saved.recoveryAttempts}"
   )
   val resumable=saved.status==AgentTaskState.Status.RUNNING ||
    saved.status==AgentTaskState.Status.RECOVERING ||
    saved.status==AgentTaskState.Status.EXECUTING ||
    saved.status==AgentTaskState.Status.WAITING_CONFIRMATION
   if(resumable){
    OutlinedButton(onClick={resume(false)},modifier=Modifier.fillMaxWidth()){Text("RESUME SAVED TASK")}
   }
   if(saved.status==AgentTaskState.Status.WAITING_CONFIRMATION){
    Button(onClick={resume(true)},modifier=Modifier.fillMaxWidth()){
     Text("CONFIRM EXACT PENDING ACTION")
    }
    Text(
     "Approval is accepted only for the fresh, action-bound checkpoint currently awaiting confirmation.",
     style=MaterialTheme.typography.bodySmall
    )
   }
   if(saved.status==AgentTaskState.Status.COMPLETE || saved.status==AgentTaskState.Status.FAILED){
    val proofStatus=if(saved.status==AgentTaskState.Status.COMPLETE)"VERIFIED COMPLETE" else "FAILED / NOT PROVEN"
    val action=saved.lastAction?.toString()?.take(180) ?: "none"
    val observation=saved.lastScreenSummary?.takeIf{it.isNotBlank()}?.take(500) ?: "no final screen observation"
    StatusCard(
     "TASK EVIDENCE",
     "$proofStatus\nGoal: ${saved.request.take(240)}\nProgress: ${saved.currentStep}/${saved.totalSteps} • recoveries ${saved.recoveryAttempts}\nLast action: $action\nFinal observation: $observation"
    )
   }
  }

  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onAccessibility,Modifier.weight(1f)){Text("Accessibility")}
   OutlinedButton(onWriteSettings,Modifier.weight(1f)){Text("Settings")}
  }
  if(shizuku?.binderAvailable==true && shizuku.permissionGranted.not()){
   OutlinedButton(onClick={runCatching{Shizuku.requestPermission(1001)};refresh++},modifier=Modifier.fillMaxWidth()){Text("Grant Shizuku")}
  }
  TextButton(onClick={refresh++;session=executor.lastSession()},modifier=Modifier.fillMaxWidth()){Text("Refresh status")}
 }
}
@Composable private fun StatusCard(title:String,detail:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(detail)}}}