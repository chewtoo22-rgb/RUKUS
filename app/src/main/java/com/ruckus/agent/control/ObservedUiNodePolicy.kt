package com.ruckus.agent.control

/**
 * Keeps accessibility observations bounded without dropping the nodes most relevant to
 * deterministic execution and verification.
 *
 * The raw accessibility tree can be large. A simple `take(MAX_NODES)` can hide the focused
 * editable field or the only visible scroll container, which then makes text-entry/scroll
 * grounding and verification fail for reasons unrelated to the action itself.
 */
object ObservedUiNodePolicy {
    const val MAX_NODES = 16

    fun select(nodes: List<UiNodeSnapshot>): List<UiNodeSnapshot> {
        val eligible = nodes
            .filter { node ->
                !node.text.isNullOrBlank() ||
                    !node.contentDescription.isNullOrBlank() ||
                    node.editable ||
                    node.focused ||
                    node.scrollable
            }
            .distinct()

        if (eligible.size <= MAX_NODES) return eligible

        val required = linkedSetOf<UiNodeSnapshot>()
        eligible.firstOrNull { it.focused && it.editable }?.let(required::add)
        eligible.firstOrNull { it.focused }?.let(required::add)
        eligible.firstOrNull { it.scrollable }?.let(required::add)

        val selected = linkedSetOf<UiNodeSnapshot>()
        eligible.take(MAX_NODES).forEach(selected::add)

        for (node in required) {
            if (node in selected) continue
            val replace = selected.lastOrNull { it !in required }
                ?: selected.lastOrNull()
                ?: break
            selected.remove(replace)
            selected.add(node)
        }

        // Preserve source-tree order so equivalent snapshots stay deterministic.
        return eligible.filter { it in selected }.take(MAX_NODES)
    }
}
