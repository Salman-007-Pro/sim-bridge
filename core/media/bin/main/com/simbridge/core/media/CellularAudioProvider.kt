package com.simbridge.core.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** PRD section 6.3. Kept separate from codec, network, telephony and UI. */
interface CellularAudioProvider {
    suspend fun probe(): AudioCapabilities
    suspend fun prepare(callId: String): AudioResult
    fun downlinkPcm(): Flow<PcmFrame>
    suspend fun writeUplink(frame: PcmFrame): AudioResult
    suspend fun stop()
}

data class AudioCapabilities(
    val downlinkCapture: Boolean,
    val uplinkInjection: Boolean,
    val requiresAdbSetup: Boolean,
    val supportedRoute: String?,
    val failureReason: String?,
)

class PcmFrame(
    val sampleRateHz: Int,
    val channels: Int,
    val samples: ShortArray,
    val timestampNanos: Long,
)

sealed interface AudioResult {
    data object Ok : AudioResult
    data class Unsupported(val reason: String) : AudioResult
    data class Failed(val reason: String) : AudioResult
}

/**
 * The only provider shipped until a physical-call loop test passes. It is never reported as implemented.
 */
object UnsupportedAudioProvider : CellularAudioProvider {
    private const val REASON = "No verified cellular audio provider for this Host model"

    override suspend fun probe() = AudioCapabilities(
        downlinkCapture = false,
        uplinkInjection = false,
        requiresAdbSetup = false,
        supportedRoute = null,
        failureReason = REASON,
    )

    override suspend fun prepare(callId: String): AudioResult = AudioResult.Unsupported(REASON)
    override fun downlinkPcm(): Flow<PcmFrame> = emptyFlow()
    override suspend fun writeUplink(frame: PcmFrame): AudioResult = AudioResult.Unsupported(REASON)
    override suspend fun stop() = Unit
}
