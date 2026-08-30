package com.ruckus.agent

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device/emulator smoke probe for Thursday hands-on runs.
 * CI compiles this test APK but does not claim execution without an Android target.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun mainActivityLaunchesWithoutImmediateCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var observed = false
            scenario.onActivity { activity ->
                observed = true
                assertFalse(activity.isFinishing)
            }
            assert(observed)
        }
    }
}
