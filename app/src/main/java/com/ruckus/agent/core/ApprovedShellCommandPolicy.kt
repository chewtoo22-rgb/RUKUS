package com.ruckus.agent.core

/**
 * Fail-closed registry for shell capabilities that may eventually be dispatched through Shizuku.
 *
 * Command IDs are semantic capabilities, never raw shell text. Keeping admission independent from
 * the transport prevents a future adapter from accidentally turning RunApprovedShell into an
 * arbitrary command-execution primitive.
 */
object ApprovedShellCommandPolicy {
    data class Decision(val allowed: Boolean, val reason: String)

    private data class CommandSpec(
        val allowedArgs: Set<String>
    )

    private val commands = mapOf(
        "device_uptime" to CommandSpec(emptySet()),
        "network_interfaces" to CommandSpec(emptySet()),
        "thermal_status" to CommandSpec(emptySet())
    )

    fun evaluate(commandId: String, args: Map<String, String>): Decision {
        val spec = commands[commandId]
            ?: return Decision(false, "Shell command id is not approved")

        val unknownArgs = args.keys - spec.allowedArgs
        if (unknownArgs.isNotEmpty()) {
            return Decision(false, "Shell command contains unapproved argument key(s)")
        }

        if (args.keys != spec.allowedArgs) {
            return Decision(false, "Shell command arguments do not match approved schema")
        }

        return Decision(true, "Approved shell capability admitted")
    }
}
