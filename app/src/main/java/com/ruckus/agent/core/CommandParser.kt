package com.ruckus.agent.core

object CommandParser {
    data class Parsed(val action: AgentAction?, val confidence: Float, val explanation: String)
    fun parse(raw: String): Parsed {
        val clean=raw.trim(); val q=clean.lowercase()
        if(q.isBlank()) return Parsed(null,0f,"Empty command")
        return when {
            q=="go home"||q=="home" -> Parsed(AgentAction.Home,.99f,"Go home")
            q=="go back"||q=="back" -> Parsed(AgentAction.Back,.99f,"Go back")
            q.startsWith("open package ") -> Parsed(AgentAction.OpenApp(q.removePrefix("open package ").trim()),.98f,"Launch exact package")
            q.startsWith("tap ") -> Parsed(AgentAction.TapLabel(clean.substringAfter(" ").trim()),.93f,"Tap visible label")
            q.startsWith("click ") -> Parsed(AgentAction.TapLabel(clean.substringAfter(" ").trim()),.93f,"Click visible label")
            q.startsWith("type ") -> Parsed(AgentAction.TypeText(clean.substringAfter(" ")),.90f,"Type into focused field")
            q.startsWith("brightness ") -> percent(q.removePrefix("brightness "))?.let{Parsed(AgentAction.SetBrightness(it),.97f,"Set brightness")}?:Parsed(null,.1f,"Invalid brightness")
            q.startsWith("volume ") -> percent(q.removePrefix("volume "))?.let{Parsed(AgentAction.SetMediaVolume(it),.97f,"Set media volume")}?:Parsed(null,.1f,"Invalid volume")
            else -> Parsed(null,.20f,"No safe deterministic action matched")
        }
    }
    private fun percent(value:String)=value.removeSuffix("%").trim().toIntOrNull()?.takeIf{it in 0..100}
}
