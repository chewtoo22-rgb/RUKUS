package com.ruckus.agent.builder

data class SmokeTestStep(
    val id: String,
    val description: String
)

data class SmokeTestPlan(
    val packageName: String,
    val apkPath: String,
    val steps: List<SmokeTestStep>
)

object SmokeTestPlanner {
    fun forHandoff(handoff: RukusHandoff): SmokeTestPlan = SmokeTestPlan(
        packageName = handoff.packageName,
        apkPath = handoff.apkPath,
        steps = listOf(
            SmokeTestStep("install", "Install the debug APK through the approved package installer path"),
            SmokeTestStep("launch", "Launch the app package"),
            SmokeTestStep("read_screen", "Capture the initial accessibility UI snapshot"),
            SmokeTestStep("check_crash", "Verify the app remains foregrounded and did not immediately crash"),
            SmokeTestStep("report", "Return screen snapshot and failure details to MUTINY")
        )
    )
}
