package com.simbridge.core.media

import com.simbridge.core.model.AudioStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGateTest {
    private val fp = "brand/model/device:16/BP2A/123:user/release-keys"
    private val both = AudioCapabilities(true, true, requiresAdbSetup = true, supportedRoute = "shizuku", failureReason = null)
    private val passing = AudioVerification(fp, true, true, true, 10, true, acousticLoopback = false)

    @Test
    fun stubIsNeverSupported() = runTest {
        val caps = UnsupportedAudioProvider.probe()
        assertFalse(caps.downlinkCapture || caps.uplinkInjection)
        assertEquals(AudioStatus.UNSUPPORTED, AudioSupportPolicy.status(caps, null, fp))
        assertTrue(UnsupportedAudioProvider.prepare("c1") is AudioResult.Unsupported)
        assertTrue(UnsupportedAudioProvider.downlinkPcm().toList().isEmpty())
    }

    @Test
    fun apiAvailabilityAloneIsOnlyLabOnly() {
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, null, fp))
    }

    @Test
    fun verifiedRequiresEveryCondition() {
        assertEquals(AudioStatus.VERIFIED, AudioSupportPolicy.status(both, passing, fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, passing.copy(buildFingerprint = "other"), fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, passing.copy(durationMinutes = 9), fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, passing.copy(hostMicIsolated = false), fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, passing.copy(survivesReboot = false), fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both, passing.copy(acousticLoopback = true), fp))
        assertEquals(AudioStatus.LAB_ONLY, AudioSupportPolicy.status(both.copy(uplinkInjection = false), passing, fp))
    }

    @Test
    fun pcmStatsSeparatesSilenceFromSignal() {
        val silence = ShortArray(16_000)
        assertFalse(PcmStats.of(silence, 16_000).looksLive)
        val tone = PcmStats.tone(1_000.0, 16_000, 1_000)
        val stats = PcmStats.of(tone, 16_000)
        assertTrue(stats.looksLive)
        assertTrue(stats.peak in 7_900..8_000)
        assertEquals(16_000, stats.samples)
    }
}
