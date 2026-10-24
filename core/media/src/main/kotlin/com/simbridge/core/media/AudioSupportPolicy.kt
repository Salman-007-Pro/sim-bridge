package com.simbridge.core.media

import com.simbridge.core.model.AudioStatus

/**
 * Evidence from one PRD 6.4 physical-call experiment on an exact device build.
 * Recorded by a person running the lab, never inferred from API availability.
 */
data class AudioVerification(
    val buildFingerprint: String,
    val downlinkIntelligible: Boolean,
    val uplinkHeardByRemote: Boolean,
    val hostMicIsolated: Boolean,
    val durationMinutes: Int,
    val survivesReboot: Boolean,
    val acousticLoopback: Boolean,
)

object AudioSupportPolicy {
    const val MIN_DURATION_MINUTES = 10

    /**
     * VERIFIED only for this exact build with both directions proven on a real call, isolated from the
     * Host microphone, for at least ten minutes and reproducible after reboot. Speaker/mic loopback never counts.
     * A positive probe without such evidence is at most LAB_ONLY.
     */
    fun status(capabilities: AudioCapabilities, verification: AudioVerification?, buildFingerprint: String): AudioStatus {
        val verified = verification != null &&
            verification.buildFingerprint == buildFingerprint &&
            verification.downlinkIntelligible &&
            verification.uplinkHeardByRemote &&
            verification.hostMicIsolated &&
            verification.durationMinutes >= MIN_DURATION_MINUTES &&
            verification.survivesReboot &&
            !verification.acousticLoopback
        return when {
            verified && capabilities.downlinkCapture && capabilities.uplinkInjection -> AudioStatus.VERIFIED
            capabilities.downlinkCapture || capabilities.uplinkInjection -> AudioStatus.LAB_ONLY
            else -> AudioStatus.UNSUPPORTED
        }
    }
}
