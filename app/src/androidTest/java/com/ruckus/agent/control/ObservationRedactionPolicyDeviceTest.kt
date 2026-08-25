package com.ruckus.agent.control

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationRedactionPolicyDeviceTest {
    @Test
    fun sensitiveAccessibilityContentIsRedactedOnAndroidRuntime() {
        val node = UiNodeSnapshot(
            text = "super-secret-pin",
            contentDescription = "PIN 2468",
            viewId = "pin",
            className = "android.widget.EditText",
            clickable = true,
            enabled = true,
            editable = true,
            sensitive = true,
            focused = true,
            scrollable = false,
        )

        val label = ObservationRedactionPolicy.label(node)

        assertEquals(ObservationRedactionPolicy.SENSITIVE_LABEL, label)
        assertFalse(label.contains("super-secret-pin"))
        assertFalse(label.contains("2468"))
    }
}
