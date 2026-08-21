package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class CommandParserTest {
 @Test fun parsesCoreCommands(){
  assertTrue(CommandParser.parse("home").action is AgentAction.Home)
  assertTrue(CommandParser.parse("tap Allow").action is AgentAction.TapLabel)
  assertTrue(CommandParser.parse("open Spotify").action is AgentAction.OpenAppByName)
  assertEquals(AgentAction.Direction.DOWN,(CommandParser.parse("scroll down").action as AgentAction.Scroll).direction)
  assertTrue(CommandParser.parse("what's on screen").action is AgentAction.InspectScreen)
  assertEquals(42,(CommandParser.parse("volume 42%").action as AgentAction.SetMediaVolume).percent)
  assertEquals(70,(CommandParser.parse("brightness 70").action as AgentAction.SetBrightness).percent)
 }
 @Test fun rejectsBadPercentAndUnknown(){
  assertNull(CommandParser.parse("volume 101").action)
  assertNull(CommandParser.parse("do whatever you want").action)
 }
 @Test fun privilegedActionsRequireConfirmation(){
  assertEquals(Risk.CONFIRM,SafetyGate.classify(AgentAction.RunApprovedShell("demo")).risk)
 }
 @Test fun inspectionAndNavigationAreSafe(){
  assertEquals(Risk.SAFE,SafetyGate.classify(AgentAction.InspectScreen).risk)
  assertEquals(Risk.SAFE,SafetyGate.classify(AgentAction.OpenAppByName("Spotify")).risk)
 }
 @Test fun plannerBuildsDeterministicSequences(){
  val plan=CommandPlanner.plan("open Spotify then volume 35 then scroll down")
  assertEquals(3,plan.actions.size)
  assertTrue(plan.rejectedParts.isEmpty())
  assertTrue(plan.actions[0] is AgentAction.OpenAppByName)
  assertTrue(plan.actions[1] is AgentAction.SetMediaVolume)
  assertTrue(plan.actions[2] is AgentAction.Scroll)
 }
 @Test fun plannerReportsUnknownSegments(){
  val plan=CommandPlanner.plan("home then make coffee")
  assertEquals(1,plan.actions.size)
  assertEquals(listOf("make coffee"),plan.rejectedParts)
 }
 @Test fun semanticFailuresGetOneSafeRetry(){
  val decision=RecoveryPolicy.decide(AgentAction.TapLabel("Allow"),"not found")
  assertTrue(decision.retry)
  assertTrue(decision.inspectFirst)
  assertEquals(1,decision.maxAttempts)
 }
 @Test fun privilegedFailuresNeverAutoRetry(){
  val decision=RecoveryPolicy.decide(AgentAction.RunApprovedShell("demo"),"failed")
  assertFalse(decision.retry)
 }
 @Test fun exactPackageLaunchRequiresForegroundPackage(){
  val ok=ActionVerifier.verify(AgentAction.OpenApp("com.spotify.music"),"pkg=launcher","pkg=com.spotify.music | Home","Opened package=com.spotify.music")
  val bad=ActionVerifier.verify(AgentAction.OpenApp("com.spotify.music"),"pkg=launcher","pkg=com.android.settings | Settings","Opened package=com.spotify.music")
  assertTrue(ok.ok); assertFalse(bad.ok)
 }
 @Test fun namedAppLaunchUsesResolvedPackage(){
  val result="Opened Spotify package=com.spotify.music"
  assertTrue(ActionVerifier.verify(AgentAction.OpenAppByName("Spotify"),"pkg=launcher","pkg=com.spotify.music | Spotify",result).ok)
 }
 @Test fun typedTextMustBeObservedOrChangeUi(){
  assertTrue(ActionVerifier.verify(AgentAction.TypeText("hello"),"pkg=x | old","pkg=x | hello","Typed text").ok)
  assertFalse(ActionVerifier.verify(AgentAction.TypeText("hello"),"pkg=x | same","pkg=x | same",null).ok)
 }
}
