package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class AppLaunchMatchPolicyTest {
    private fun candidate(pkg: String, label: String) = AppLaunchMatchPolicy.Candidate(pkg, label)

    @Test fun uniqueExactLabelWinsOverPartialMatches() {
        val resolved = AppLaunchMatchPolicy.resolve(
            "Chrome",
            listOf(
                candidate("com.android.chrome", "Chrome"),
                candidate("com.example.chrome.beta", "Chrome Beta")
            )
        )
        assertEquals("com.android.chrome", resolved?.packageName)
    }

    @Test fun uniquePartialLabelIsAllowed() {
        val resolved = AppLaunchMatchPolicy.resolve(
            "Maps",
            listOf(
                candidate("com.google.android.apps.maps", "Google Maps"),
                candidate("com.example.music", "Music")
            )
        )
        assertEquals("com.google.android.apps.maps", resolved?.packageName)
    }

    @Test fun duplicateExactLabelsFailClosed() {
        val resolved = AppLaunchMatchPolicy.resolve(
            "Gallery",
            listOf(
                candidate("com.vendor.gallery", "Gallery"),
                candidate("com.example.gallery", "Gallery")
            )
        )
        assertNull(resolved)
    }

    @Test fun ambiguousPartialLabelsFailClosed() {
        val resolved = AppLaunchMatchPolicy.resolve(
            "Photo",
            listOf(
                candidate("com.vendor.photos", "Photos"),
                candidate("com.example.photoeditor", "Photo Editor")
            )
        )
        assertNull(resolved)
    }

    @Test fun duplicateActivitiesFromSamePackageDoNotCreateFalseAmbiguity() {
        val resolved = AppLaunchMatchPolicy.resolve(
            "Camera",
            listOf(
                candidate("com.vendor.camera", "Camera"),
                candidate("com.vendor.camera", "Camera")
            )
        )
        assertEquals("com.vendor.camera", resolved?.packageName)
    }

    @Test fun blankRequestFailsClosed() {
        assertNull(AppLaunchMatchPolicy.resolve("   ", listOf(candidate("com.example", "Example"))))
    }
}
