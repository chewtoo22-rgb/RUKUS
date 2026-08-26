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
 @Test fun privilegedActionsRequireConfirmation(){ assertEquals(Risk.CONFIRM,SafetyGate.classify(AgentAction.RunApprovedShell("demo")).risk) }
 @Test fun inspectionAndNavigationAreSafe(){
  assertEquals(Risk.SAFE,SafetyGate.classify(AgentAction.InspectScreen).risk)
  assertEquals(Risk.SAFE,SafetyGate.classify(AgentAction.OpenAppByName("Spotify")).risk)
 }
 @Test fun plannerBuildsDeterministicSequences(){
  val plan=CommandPlanner.plan("open Spotify then volume 35 then scroll down")
  assertEquals(3,plan.actions.size); assertTrue(plan.rejectedParts.isEmpty())
  assertTrue(plan.actions[0] is AgentAction.OpenAppByName); assertTrue(plan.actions[1] is AgentAction.SetMediaVolume); assertTrue(plan.actions[2] is AgentAction.Scroll)
 }
 @Test fun plannerReportsUnknownSegments(){
  val plan=CommandPlanner.plan("home then make coffee")
  assertEquals(1,plan.actions.size); assertEquals(listOf("make coffee"),plan.rejectedParts)
 }
 @Test fun semanticFailuresGetOneSafeRetry(){
  val decision=RecoveryPolicy.decide(AgentAction.TapLabel("Allow"),"not found")
  assertTrue(decision.retry); assertTrue(decision.inspectFirst); assertEquals(1,decision.maxAttempts)
 }
 @Test fun privilegedFailuresNeverAutoRetry(){ assertFalse(RecoveryPolicy.decide(AgentAction.RunApprovedShell("demo"),"failed").retry) }
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
 @Test fun adaptiveTapChoosesUniqueCloseVisibleLabel(){
  val plan=AdaptiveRecoveryPlanner.replan(AgentAction.TapLabel("Continue"),"pkg=x | labels=Contnue • Cancel • Settings","not found")
  assertTrue(plan.alternate is AgentAction.TapLabel)
  assertEquals("Contnue",(plan.alternate as AgentAction.TapLabel).label)
  assertTrue(plan.confidence >= .72f)
 }
 @Test fun adaptiveTapRefusesAmbiguousGuess(){
  val plan=AdaptiveRecoveryPlanner.replan(AgentAction.TapLabel("Pay"),"pkg=x | labels=Pay now • Pay later • Cancel","not found")
  assertNull(plan.alternate)
 }
 @Test fun adaptiveScrollUsesBoundedGestureFallback(){
  val plan=AdaptiveRecoveryPlanner.replan(AgentAction.Scroll(AgentAction.Direction.DOWN),"pkg=x | labels=A • B","verification failed")
  assertTrue(plan.alternate is AgentAction.Swipe)
  assertEquals(Risk.SAFE,SafetyGate.classify(plan.alternate!!).risk)
 }
 @Test fun adaptiveRecoveryNeverInventsPrivilegedFallback(){
  val plan=AdaptiveRecoveryPlanner.replan(AgentAction.RunApprovedShell("demo"),"pkg=x","failed")
  assertNull(plan.alternate)
 }
 @Test fun recoveryEquivalenceAllowsIntentPreservingAlternates(){
  assertTrue(RecoveryEquivalence.canSubstitute(AgentAction.TapLabel("Continue"),AgentAction.TapLabel("Contnue"),.80f))
  assertTrue(RecoveryEquivalence.canSubstitute(AgentAction.Scroll(AgentAction.Direction.DOWN),AgentAction.Swipe(500f,1500f,500f,500f,400),.80f))
  assertTrue(RecoveryEquivalence.canSubstitute(AgentAction.Swipe(500f,500f,500f,1500f,400),AgentAction.Scroll(AgentAction.Direction.UP),.76f))
 }
 @Test fun recoveryEquivalenceRejectsSafeButDifferentIntent(){
  assertFalse(RecoveryEquivalence.canSubstitute(AgentAction.Back,AgentAction.Home,.99f))
  assertFalse(RecoveryEquivalence.canSubstitute(AgentAction.TapLabel("Pay"),AgentAction.TapLabel("Cancel"),.60f))
  assertFalse(RecoveryEquivalence.canSubstitute(AgentAction.Scroll(AgentAction.Direction.DOWN),AgentAction.Swipe(500f,500f,500f,1500f,400),.90f))
 }
 @Test fun resumeStartsAtFirstUnverifiedCheckpoint(){
  val request="home then scroll down then volume 25"
  val plan=CommandPlanner.plan(request)
  val session=PersistedTaskSession(request,1,3,"Home","pkg=launcher",0,AgentTaskState.Status.RUNNING,123L,PlanFingerprint.of(plan))
  val resume=ResumePolicy.decide(session,plan)
  assertTrue(resume.allowed); assertEquals(1,resume.startStep)
 }
 @Test fun waitingSessionResumesAtCurrentCheckpoint(){
  val request="privileged shell demo"
  val plan=CommandPlanner.Plan(listOf(AgentAction.RunApprovedShell("demo")),emptyList())
  val session=PersistedTaskSession(request,0,1,plan.actions[0].toString(),"pkg=launcher",0,AgentTaskState.Status.WAITING_CONFIRMATION,123L,PlanFingerprint.of(plan))
  val resume=ResumePolicy.decide(session,plan)
  assertTrue(resume.allowed); assertEquals(0,resume.startStep)
 }
 @Test fun resumeRejectsCompletedFailedAndChangedPlans(){
  val request="home then scroll down"
  val plan=CommandPlanner.plan(request)
  val complete=PersistedTaskSession(request,2,2,"Scroll","pkg=x",0,AgentTaskState.Status.COMPLETE,123L,PlanFingerprint.of(plan))
  val failed=complete.copy(currentStep=1,status=AgentTaskState.Status.FAILED)
  val changed=complete.copy(currentStep=1,totalSteps=3,status=AgentTaskState.Status.RUNNING)
  assertFalse(ResumePolicy.decide(complete,plan).allowed)
  assertFalse(ResumePolicy.decide(failed,plan).allowed)
  assertFalse(ResumePolicy.decide(changed,plan).allowed)
 }
 @Test fun resumeRejectsSameLengthPlanWithDifferentSemantics(){
  val oldPlan=CommandPlanner.Plan(listOf(AgentAction.Home,AgentAction.Scroll(AgentAction.Direction.DOWN)),emptyList())
  val newPlan=CommandPlanner.Plan(listOf(AgentAction.Home,AgentAction.Scroll(AgentAction.Direction.UP)),emptyList())
  val session=PersistedTaskSession("saved",1,2,"Home","pkg=x",0,AgentTaskState.Status.RUNNING,123L,PlanFingerprint.of(oldPlan))
  val decision=ResumePolicy.decide(session,newPlan)
  assertFalse(decision.allowed)
  assertTrue(decision.reason.contains("semantics",ignoreCase=true))
 }
 @Test fun resumeRejectsLegacyCheckpointWithoutFingerprint(){
  val plan=CommandPlanner.plan("home then scroll down")
  val session=PersistedTaskSession("home then scroll down",1,2,"Home","pkg=x",0,AgentTaskState.Status.RUNNING,123L)
  assertFalse(ResumePolicy.decide(session,plan).allowed)
 }
 @Test fun resumeRejectsRecoveryCountBeyondBudget(){
  val request="home then scroll down"
  val plan=CommandPlanner.plan(request)
  val session=PersistedTaskSession(request,1,2,"Home","pkg=x",RecoveryBudget.MAX_TOTAL_ATTEMPTS+1,AgentTaskState.Status.RECOVERING,123L,PlanFingerprint.of(plan))
  val decision=ResumePolicy.decide(session,plan)
  assertFalse(decision.allowed)
  assertTrue(decision.reason.contains("recovery",ignoreCase=true))
  assertTrue(decision.reason.contains("budget",ignoreCase=true))
 }
 @Test fun fingerprintIsStableAndArgumentOrderIndependent(){
  val a=CommandPlanner.Plan(listOf(AgentAction.RunApprovedShell("demo",mapOf("b" to "2","a" to "1"))),emptyList())
  val b=CommandPlanner.Plan(listOf(AgentAction.RunApprovedShell("demo",mapOf("a" to "1","b" to "2"))),emptyList())
  assertEquals(PlanFingerprint.of(a),PlanFingerprint.of(b))
 }
 @Test fun recoveryBudgetAllowsOnlyBoundedAutonomousAttempts(){
  assertTrue(RecoveryBudget.decide(0).allowed)
  assertTrue(RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS-1).allowed)
  assertFalse(RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS).allowed)
  assertFalse(RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS+1).allowed)
 }
 @Test fun recoveryBudgetRejectsCorruptNegativeCount(){ assertFalse(RecoveryBudget.decide(-1).allowed) }
 @Test fun completionGateRequiresEveryStepVerified(){
  val plan=CommandPlanner.plan("home then scroll down")
  assertFalse(TaskCompletionGate.evaluate(plan,1,"pkg=launcher | labels=Home").ok)
 }
 @Test fun completionGateRequiresRequestedPackageToRemainForeground(){
  val plan=CommandPlanner.Plan(listOf(AgentAction.OpenApp("com.spotify.music")), emptyList())
  assertTrue(TaskCompletionGate.evaluate(plan,1,"pkg=com.spotify.music | labels=Spotify").ok)
  assertFalse(TaskCompletionGate.evaluate(plan,1,"pkg=com.android.settings | labels=Settings").ok)
 }
 @Test fun completionGateRequiresTerminalTypedTextEvidence(){
  val plan=CommandPlanner.Plan(listOf(AgentAction.TypeText("hello")), emptyList())
  assertTrue(TaskCompletionGate.evaluate(plan,1,"pkg=x | labels=hello").ok)
  assertFalse(TaskCompletionGate.evaluate(plan,1,"pkg=x | labels=other").ok)
 }
 @Test fun completionGateRequiresObservableUiCheckpointForNavigation(){
  val plan=CommandPlanner.Plan(listOf(AgentAction.Home), emptyList())
  assertTrue(TaskCompletionGate.evaluate(plan,1,"pkg=launcher | labels=Home").ok)
  assertFalse(TaskCompletionGate.evaluate(plan,1,null).ok)
 }
}
