package com.ruckus.agent.core

sealed interface AgentAction {
    data class OpenApp(val packageName: String) : AgentAction
    data class OpenAppByName(val appName: String) : AgentAction
    data object Back : AgentAction
    data object Home : AgentAction
    data class Tap(val x: Float, val y: Float) : AgentAction
    data class TapLabel(val label: String) : AgentAction
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long = 350) : AgentAction
    data class Scroll(val direction: Direction) : AgentAction
    data class TypeText(val text: String) : AgentAction
    data object InspectScreen : AgentAction
    data class SetBrightness(val percent: Int) : AgentAction
    data class SetMediaVolume(val percent: Int) : AgentAction
    data class RunApprovedShell(val commandId: String, val args: Map<String, String> = emptyMap()) : AgentAction

    enum class Direction { UP, DOWN }
}
