package com.simbridge.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {

    @Test
    fun outgoingCallFollowsAndroidOrder() {
        val path = listOf(CallStatus.IDLE, CallStatus.CONNECTING, CallStatus.DIALING, CallStatus.ACTIVE, CallStatus.HOLD,
            CallStatus.ACTIVE, CallStatus.ENDING, CallStatus.ENDED)
        path.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", a.canTransitionTo(b)) }
    }

    @Test
    fun terminalCallStatesAreSinks() {
        for (next in CallStatus.entries.filter { it != CallStatus.ENDED }) {
            assertFalse(CallStatus.ENDED.canTransitionTo(next))
        }
        assertFalse(CallStatus.FAILED.canTransitionTo(CallStatus.ACTIVE))
        assertFalse(CallStatus.ACTIVE.canTransitionTo(CallStatus.RINGING))
        assertFalse(CallStatus.ACTIVE.canTransitionTo(CallStatus.IDLE))
    }

    @Test
    fun hostUnreachableCanResyncToRealState() {
        assertTrue(CallStatus.ACTIVE.canTransitionTo(CallStatus.HOST_UNREACHABLE))
        assertTrue(CallStatus.HOST_UNREACHABLE.canTransitionTo(CallStatus.ACTIVE))
        assertTrue(CallStatus.HOST_UNREACHABLE.canTransitionTo(CallStatus.ENDED))
    }

    @Test
    fun smsStatusNeverRegresses() {
        assertTrue(SmsStatus.QUEUED.canAdvanceTo(SmsStatus.SENT_TO_HOST))
        assertTrue(SmsStatus.SENT_TO_HOST.canAdvanceTo(SmsStatus.SENT))
        assertTrue(SmsStatus.SENT.canAdvanceTo(SmsStatus.DELIVERED))
        assertFalse(SmsStatus.DELIVERED.canAdvanceTo(SmsStatus.SENT))
        assertFalse(SmsStatus.DELIVERED.canAdvanceTo(SmsStatus.DELIVERED))
        assertFalse(SmsStatus.SENT.canAdvanceTo(SmsStatus.SUBMITTED_TO_MODEM))
        assertTrue(SmsStatus.SUBMITTED_TO_MODEM.canAdvanceTo(SmsStatus.FAILED))
        assertFalse(SmsStatus.FAILED.canAdvanceTo(SmsStatus.SENT))
        assertFalse(SmsStatus.RECEIVED.canAdvanceTo(SmsStatus.DELIVERED))
    }

    @Test
    fun dialPolicyBlocksEmergencyAndPremium() {
        val policy = DialPolicy()
        assertEquals(DialPolicy.Verdict.BLOCKED_EMERGENCY, policy.evaluate("1122"))
        assertEquals(DialPolicy.Verdict.BLOCKED_EMERGENCY, policy.evaluate(" 1 5 "))
        assertEquals(DialPolicy.Verdict.BLOCKED_PREMIUM, policy.evaluate("0900-123456"))
        assertEquals(DialPolicy.Verdict.ALLOWED, policy.evaluate("+92 300 1234567"))
        assertEquals(DialPolicy.Verdict.INVALID, policy.evaluate("ab"))
    }

    @Test
    fun phoneNormalizationAndRedaction() {
        assertEquals("+923001234567", PhoneNumbers.normalize("+92 (300) 123-4567"))
        assertEquals("03001234567", PhoneNumbers.normalize("0300-1234567"))
        assertEquals("030******67", Redaction.phone("03001234567"))
        assertEquals("<5 chars>", Redaction.body("hello"))
    }
}
