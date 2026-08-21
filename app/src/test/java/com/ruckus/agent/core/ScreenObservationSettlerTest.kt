package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class ScreenObservationSettlerTest {
    @Test fun waitsPastStaleBaselineUntilChangedStateStabilizes() {
        val samples=ArrayDeque(listOf(
            "pkg=launcher | labels=Home",
            "pkg=launcher | labels=Home",
            "pkg=com.spotify.music | labels=Spotify",
            "pkg=com.spotify.music | labels=Spotify"
        ))
        val result=ScreenObservationSettler.observe(
            before="pkg=launcher | labels=Home",
            sampler={ samples.removeFirstOrNull() },
            pause={}
        )
        assertEquals("pkg=com.spotify.music | labels=Spotify",result.screen)
        assertEquals(4,result.samples)
        assertTrue(result.stable)
        assertTrue(result.changedFromBefore)
    }

    @Test fun noChangeConsumesBoundedWindowInsteadOfSettlingOnStaleUi() {
        var calls=0
        val result=ScreenObservationSettler.observe(
            before="pkg=x | labels=Same",
            maxSamples=4,
            sampler={ calls++; "pkg=x | labels=Same" },
            pause={}
        )
        assertEquals(4,calls)
        assertEquals(4,result.samples)
        assertFalse(result.stable)
        assertFalse(result.changedFromBefore)
        assertEquals("pkg=x | labels=Same",result.screen)
    }

    @Test fun nullBaselineCanSettleOnTwoMatchingSnapshots() {
        var calls=0
        val result=ScreenObservationSettler.observe(
            before=null,
            sampler={ calls++; "pkg=x | labels=Ready" },
            pause={}
        )
        assertEquals(2,calls)
        assertTrue(result.stable)
        assertEquals("pkg=x | labels=Ready",result.screen)
    }

    @Test fun invalidSamplingBoundsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScreenObservationSettler.observe(before=null,maxSamples=0,sampler={ null })
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScreenObservationSettler.observe(before=null,maxSamples=1,requiredStableSamples=2,sampler={ null })
        }
    }
}
