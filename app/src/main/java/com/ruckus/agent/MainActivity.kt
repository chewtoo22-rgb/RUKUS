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
import com.ruckus.agent.core.RuckusExecutor
import com.ruckus.agent.personality.RuckusPersona

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val executor=RuckusExecutor(this);setContent{MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize(),color=Color(0xFF090909)){Dashboard(executor,{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},{startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:$packageName")))})}}}}
}

@Composable private fun Dashboard(executor:RuckusExecutor,onAccessibility:()->Unit,onWriteSettings:()->Unit){
 var command by remember{mutableStateOf("")};var result by remember{mutableStateOf("Ready. Try: home, back, volume 30, brightness 50, tap <label>, type <text>")}
 val shizuku=runCatching{ShizukuStateReader.read()}.getOrNull()
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(RuckusPersona.NAME,style=MaterialTheme.typography.displayMedium);Text(RuckusPersona.TAGLINE);HorizontalDivider()
  StatusCard("CONTROL SERVICE",if(RuckusAccessibilityService.instance!=null)"ONLINE" else "OFFLINE — enable Accessibility")
  StatusCard("SHIZUKU",when{shizuku?.permissionGranted==true->"CONNECTED + GRANTED";shizuku?.binderAvailable==true->"CONNECTED — permission needed";else->"OFFLINE"})
  OutlinedTextField(command,{command=it},Modifier.fillMaxWidth(),label={Text("Tell RUKUS what to do")},singleLine=true)
  Button(onClick={val report=executor.run(command);result=if(report.ok)"✓ ${report.message}" else if(report.needsConfirmation)"CONFIRM: ${report.message}" else "✕ ${report.message}"},modifier=Modifier.fillMaxWidth(),enabled=command.isNotBlank()){Text("EXECUTE")}
  Text(result,style=MaterialTheme.typography.bodyMedium)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onAccessibility,Modifier.weight(1f)){Text("Accessibility")};OutlinedButton(onWriteSettings,Modifier.weight(1f)){Text("Settings")}}
 }
}
@Composable private fun StatusCard(title:String,detail:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(detail)}}}
