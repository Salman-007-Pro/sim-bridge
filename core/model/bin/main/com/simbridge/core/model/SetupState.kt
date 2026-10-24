package com.simbridge.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceRole { HOST, CLIENT }

@Serializable
enum class SetupState {
    NOT_PAIRED,
    PAIRING,
    PAIRING_CONFIRMATION,
    CONNECTED_CONTROL_ONLY,
    CONNECTED_AUDIO_READY,
    DEGRADED,
    HOST_OFFLINE,
    REVOKED;

    val isConnected: Boolean
        get() = this == CONNECTED_CONTROL_ONLY || this == CONNECTED_AUDIO_READY || this == DEGRADED
}

/** Per-feature capability, reported separately for calls, SMS and audio. */
@Serializable
enum class CapabilityStatus { SUPPORTED, NEEDS_SETUP, UNSUPPORTED, UNKNOWN }

/**
 * Audio is never [VERIFIED] because an API exists; only a recorded physical call loop test may set it.
 */
@Serializable
enum class AudioStatus { UNSUPPORTED, LAB_ONLY, VERIFIED }
