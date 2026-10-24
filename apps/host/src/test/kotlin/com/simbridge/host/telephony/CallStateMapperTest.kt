package com.simbridge.host.telephony

import android.telecom.Call
import android.telecom.DisconnectCause
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStateMapperTest {
    @Test
    fun mapsTelecomStatesToBridgeModel() {
        assertEquals(CallStatus.CONNECTING, CallStateMapper.status(Call.STATE_NEW, null))
        assertEquals(CallStatus.CONNECTING, CallStateMapper.status(Call.STATE_CONNECTING, null))
        assertEquals(CallStatus.CONNECTING, CallStateMapper.status(Call.STATE_SELECT_PHONE_ACCOUNT, null))
        assertEquals(CallStatus.DIALING, CallStateMapper.status(Call.STATE_DIALING, null))
        assertEquals(CallStatus.RINGING, CallStateMapper.status(Call.STATE_RINGING, null))
        assertEquals(CallStatus.RINGING, CallStateMapper.status(Call.STATE_SIMULATED_RINGING, null))
        assertEquals(CallStatus.ACTIVE, CallStateMapper.status(Call.STATE_ACTIVE, null))
        assertEquals(CallStatus.HOLD, CallStateMapper.status(Call.STATE_HOLDING, null))
        assertEquals(CallStatus.ENDING, CallStateMapper.status(Call.STATE_DISCONNECTING, null))
        assertEquals(CallStatus.ENDED, CallStateMapper.status(Call.STATE_DISCONNECTED, DisconnectCause.REMOTE))
        assertEquals(CallStatus.FAILED, CallStateMapper.status(Call.STATE_DISCONNECTED, DisconnectCause.ERROR))
    }

    @Test
    fun mapsDirectionAndReasons() {
        assertEquals(CallDirection.INCOMING, CallStateMapper.direction(Call.Details.DIRECTION_INCOMING))
        assertEquals(CallDirection.OUTGOING, CallStateMapper.direction(Call.Details.DIRECTION_OUTGOING))
        assertEquals("MISSED", CallStateMapper.reason(DisconnectCause.MISSED))
        assertEquals("BUSY", CallStateMapper.reason(DisconnectCause.BUSY))
        assertEquals("REJECTED", CallStateMapper.reason(DisconnectCause.REJECTED))
        assertNull(CallStateMapper.reason(null))
    }
}
