package com.simbridge.host.telephony

import android.telecom.Call
import android.telecom.DisconnectCause
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus

/** Maps platform Telecom states onto the bridge's call model. */
object CallStateMapper {
    fun status(state: Int, disconnectCode: Int?): CallStatus = when (state) {
        Call.STATE_DIALING -> CallStatus.DIALING
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallStatus.RINGING
        Call.STATE_ACTIVE -> CallStatus.ACTIVE
        Call.STATE_HOLDING -> CallStatus.HOLD
        Call.STATE_DISCONNECTING -> CallStatus.ENDING
        Call.STATE_DISCONNECTED -> if (disconnectCode == DisconnectCause.ERROR) CallStatus.FAILED else CallStatus.ENDED
        else -> CallStatus.CONNECTING
    }

    fun direction(callDirection: Int): CallDirection =
        if (callDirection == Call.Details.DIRECTION_INCOMING) CallDirection.INCOMING else CallDirection.OUTGOING

    fun reason(disconnectCode: Int?): String? = when (disconnectCode) {
        null -> null
        DisconnectCause.MISSED -> "MISSED"
        DisconnectCause.REJECTED -> "REJECTED"
        DisconnectCause.BUSY -> "BUSY"
        DisconnectCause.LOCAL -> "LOCAL"
        DisconnectCause.REMOTE -> "REMOTE"
        DisconnectCause.ERROR -> "ERROR"
        DisconnectCause.CANCELED -> "CANCELED"
        DisconnectCause.RESTRICTED -> "RESTRICTED"
        DisconnectCause.ANSWERED_ELSEWHERE -> "ANSWERED_ELSEWHERE"
        DisconnectCause.UNKNOWN -> null
        else -> "OTHER"
    }
}
