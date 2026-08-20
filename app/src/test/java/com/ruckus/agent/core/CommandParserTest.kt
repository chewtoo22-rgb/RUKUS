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
}
