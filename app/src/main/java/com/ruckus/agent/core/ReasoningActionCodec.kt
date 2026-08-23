package com.ruckus.agent.core

/**
 * Strict text-to-typed-action boundary for a future natural-language reasoning model.
 *
 * The model is never allowed to hand arbitrary commands directly to the executor. It emits this
 * deliberately small line-oriented DSL, which is decoded into AgentAction values and then must
 * still pass ObservedPlanProposal admission, intent binding, grounding, freshness, safety, and
 * verification.
 *
 * Format: one action per line. Arguments are separated by a single TAB. Text arguments support
 * only \\, \t and \n escapes. Unknown verbs, malformed escaping, extra arguments, raw coordinate
 * gestures, and privileged shell actions fail closed.
 */
object ReasoningActionCodec {
    data class DecodeResult(
        val actions: List<AgentAction> = emptyList(),
        val error: String? = null,
    ) {
        val allowed: Boolean get() = error == null
    }

    fun decode(text: String): DecodeResult {
        if (text.isBlank()) return DecodeResult(error = "Reasoning action payload is blank")

        val lines = text.replace("\r\n", "\n")
            .split('\n')
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return DecodeResult(error = "Reasoning action payload is blank")
        if (lines.size > PlanAdmissionPolicy.MAX_ACTIONS) {
            return DecodeResult(error = "Reasoning action payload exceeds ${PlanAdmissionPolicy.MAX_ACTIONS} actions")
        }

        val actions = mutableListOf<AgentAction>()
        lines.forEachIndexed { index, rawLine ->
            val parts = rawLine.split('\t')
            val verb = parts.firstOrNull()?.trim()?.uppercase().orEmpty()
            val args = parts.drop(1)

            val action = when (verb) {
                "INSPECT" -> noArgs(index, args, AgentAction.InspectScreen) ?: return failure(index, verb)
                "BACK" -> noArgs(index, args, AgentAction.Back) ?: return failure(index, verb)
                "HOME" -> noArgs(index, args, AgentAction.Home) ?: return failure(index, verb)
                "OPEN_APP" -> oneTextArg(index, verb, args)?.let(AgentAction::OpenApp) ?: return failure(index, verb)
                "OPEN_APP_NAME" -> oneTextArg(index, verb, args)?.let(AgentAction::OpenAppByName) ?: return failure(index, verb)
                "TAP_LABEL" -> oneTextArg(index, verb, args)?.let(AgentAction::TapLabel) ?: return failure(index, verb)
                "TYPE_TEXT" -> oneTextArg(index, verb, args, allowBlank = false)?.let(AgentAction::TypeText) ?: return failure(index, verb)
                "SCROLL_UP" -> noArgs(index, args, AgentAction.Scroll(AgentAction.Direction.UP)) ?: return failure(index, verb)
                "SCROLL_DOWN" -> noArgs(index, args, AgentAction.Scroll(AgentAction.Direction.DOWN)) ?: return failure(index, verb)
                "SET_BRIGHTNESS" -> onePercentArg(args)?.let(AgentAction::SetBrightness) ?: return failure(index, verb)
                "SET_MEDIA_VOLUME" -> onePercentArg(args)?.let(AgentAction::SetMediaVolume) ?: return failure(index, verb)
                else -> return DecodeResult(error = "Step ${index + 1}: unsupported reasoning action '$verb'")
            }
            actions += action
        }

        val admission = PlanAdmissionPolicy.evaluate(actions)
        if (!admission.allowed) return DecodeResult(error = admission.reason)
        val reasoning = ReasoningPlanPolicy.evaluate(actions)
        if (!reasoning.allowed) return DecodeResult(error = reasoning.reason)

        return DecodeResult(actions = actions)
    }

    private fun noArgs(index: Int, args: List<String>, action: AgentAction): AgentAction? =
        if (args.isEmpty()) action else null

    private fun oneTextArg(
        index: Int,
        verb: String,
        args: List<String>,
        allowBlank: Boolean = false,
    ): String? {
        if (args.size != 1) return null
        val value = unescape(args.single()) ?: return null
        if (!allowBlank && value.isBlank()) return null
        return value
    }

    private fun onePercentArg(args: List<String>): Int? {
        if (args.size != 1) return null
        val value = args.single().trim().toIntOrNull() ?: return null
        return value.takeIf { it in 0..100 }
    }

    private fun unescape(value: String): String? {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= value.length) return null
            when (val next = value[i + 1]) {
                '\\' -> out.append('\\')
                't' -> out.append('\t')
                'n' -> out.append('\n')
                else -> return null
            }
            i += 2
        }
        return out.toString()
    }

    private fun failure(index: Int, verb: String): DecodeResult =
        DecodeResult(error = "Step ${index + 1}: malformed arguments for '$verb'")
}
