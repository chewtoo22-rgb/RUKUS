package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningNavigationVerificationTest {
    @Test
    fun back_requires_observable_ui_change_even_if_adapter_reports_success() {
        val unchanged = ActionVerifier.verify(
            AgentAction.Back,
            before = "pkg=com.example\ntext=Page A",
            after = "pkg=com.example\ntext=Page A",
            result = "Back dispatched",
        )
        val changed = ActionVerifier.verify(
            AgentAction.Back,
            before = "pkg=com.example\ntext=Page B",
            after = "pkg=com.example\ntext=Page A",
            result = "Back dispatched",
        )

        assertFalse(unchanged.ok)
        assertTrue(changed.ok)
    }

    @Test
    fun home_requires_observable_ui_change_even_if_adapter_reports_success() {
        val unchanged = ActionVerifier.verify(
            AgentAction.Home,
            before = "pkg=com.example\ntext=Page A",
            after = "pkg=com.example\ntext=Page A",
            result = "Home dispatched",
        )
        val changed = ActionVerifier.verify(
            AgentAction.Home,
            before = "pkg=com.example\ntext=Page A",
            after = "pkg=com.android.launcher\ntext=Home",
            result = "Home dispatched",
        )

        assertFalse(unchanged.ok)
        assertTrue(changed.ok)
    }
}
