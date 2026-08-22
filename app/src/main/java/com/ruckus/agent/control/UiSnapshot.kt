package com.ruckus.agent.control

/** Lightweight semantic snapshot of currently visible accessibility nodes. */
data class UiNodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val sensitive: Boolean,
    val focused: Boolean,
)
