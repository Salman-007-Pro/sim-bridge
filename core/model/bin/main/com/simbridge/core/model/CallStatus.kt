package com.simbridge.core.model

import kotlinx.serialization.Serializable

/**
 * Call lifecycle as seen by the bridge. The Host's telephony state is authoritative;
 * [HOST_UNREACHABLE] is a Client-side projection used while the link is down.
 */
@Serializable
enum class CallStatus {
    IDLE,
    CONNECTING,
    DIALING,
    RINGING,
    ACTIVE,
    HOLD,
    ENDING,
    ENDED,
    FAILED,
    HOST_UNREACHABLE;

    val isTerminal: Boolean get() = this == ENDED || this == FAILED

    val isLive: Boolean get() = !isTerminal && this != IDLE

    fun canTransitionTo(next: CallStatus): Boolean =
        next == this || next in ALLOWED.getValue(this)

    private companion object {
        private val AFTER_START = setOf(ENDING, ENDED, FAILED, HOST_UNREACHABLE)

        val ALLOWED: Map<CallStatus, Set<CallStatus>> = mapOf(
            IDLE to setOf(CONNECTING, DIALING, RINGING, ENDED, FAILED),
            CONNECTING to setOf(DIALING, RINGING, ACTIVE) + AFTER_START,
            DIALING to setOf(CONNECTING, ACTIVE, HOLD) + AFTER_START,
            RINGING to setOf(CONNECTING, ACTIVE) + AFTER_START,
            ACTIVE to setOf(HOLD) + AFTER_START,
            HOLD to setOf(ACTIVE) + AFTER_START,
            ENDING to setOf(ENDED, FAILED, HOST_UNREACHABLE),
            HOST_UNREACHABLE to setOf(CONNECTING, DIALING, RINGING, ACTIVE, HOLD, ENDING, ENDED, FAILED),
            ENDED to emptySet(),
            FAILED to emptySet(),
        )
    }
}

@Serializable
enum class CallDirection { INCOMING, OUTGOING }
