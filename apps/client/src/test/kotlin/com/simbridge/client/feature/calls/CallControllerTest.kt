package com.simbridge.client.feature.calls

import com.simbridge.client.session.HostLink
import com.simbridge.client.session.RequestOutcome
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.protocol.DialRequest
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.ProtocolCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControllerTest {
    private class ScriptedLink(vararg outcomes: RequestOutcome) : HostLink {
        val queue = ArrayDeque(outcomes.toList())
        val dials = mutableListOf<DialRequest>()

        override suspend fun <T> request(type: MessageType, serializer: KSerializer<T>, body: T, timeoutMs: Long): RequestOutcome {
            if (body is DialRequest) dials += body
            return queue.removeFirst()
        }

        override suspend fun <T> send(type: MessageType, serializer: KSerializer<T>, body: T) = true
    }

    private val ack = RequestOutcome.Acked(
        Envelope(1, "a", "s", 1, 0, MessageType.ACK, body = ProtocolCodec.encodeBody(MessageType.ACK, Ack.serializer(), Ack("placed"))),
    )

    @Test
    fun timeoutIsUnknownAndIsNeverRetriedAutomatically() = runTest {
        val link = ScriptedLink(RequestOutcome.Timeout)
        val controller = CallController(link)
        val attempt = controller.dial("0300 1234567")
        assertEquals(DialState.UNKNOWN, attempt.state)
        assertEquals(1, link.dials.size)
    }

    @Test
    fun recheckReusesTheSameIdempotencyKey() = runTest {
        val link = ScriptedLink(RequestOutcome.Timeout, ack)
        val controller = CallController(link)
        controller.dial("03001234567")
        val rechecked = controller.recheck()
        assertEquals(DialState.PLACED, rechecked?.state)
        assertEquals(2, link.dials.size)
        assertEquals(link.dials[0].idempotencyKey, link.dials[1].idempotencyKey)
    }

    @Test
    fun hostDuplicateWithUnknownOutcomeStaysUnknown() = runTest {
        val duplicate = RequestOutcome.Rejected(ErrorBody(ErrorCode.DUPLICATE_OUTCOME_UNKNOWN, "already handling"))
        val controller = CallController(ScriptedLink(duplicate))
        assertEquals(DialState.UNKNOWN, controller.dial("03001234567").state)
    }

    @Test
    fun emergencyAndInvalidNumbersNeverReachTheHost() = runTest {
        val link = ScriptedLink()
        val controller = CallController(link)
        assertEquals(DialState.FAILED, controller.dial("1122").state)
        assertEquals(DialState.FAILED, controller.dial("x").state)
        assertTrue(link.dials.isEmpty())
    }

    @Test
    fun outgoingCallStateClearsThePendingDial() = runTest {
        val controller = CallController(ScriptedLink(ack))
        controller.dial("+92 300 1234567")
        controller.onCallState(CallInfo("c1", "+923001234567", CallDirection.OUTGOING, CallStatus.DIALING, 1))
        assertNull(controller.dial.value)
    }

    @Test
    fun activeCallsNeverRegressFromAFinalState() {
        val calls = ActiveCalls()
        val ringing = CallInfo("c1", "0300", CallDirection.INCOMING, CallStatus.RINGING, 1)
        calls.apply(ringing)
        calls.apply(ringing.copy(status = CallStatus.ENDED, disconnectReason = "MISSED"))
        assertEquals(CallStatus.ENDED, calls.apply(ringing).status)

        val other = CallInfo("c2", "0301", CallDirection.OUTGOING, CallStatus.ACTIVE, 5)
        calls.apply(other)
        assertEquals("c2", ActiveCalls.primary(calls.calls.value.values)?.callId)
        calls.markUnreachable()
        assertEquals(CallStatus.HOST_UNREACHABLE, calls.calls.value.getValue("c2").status)
        assertEquals(CallStatus.ENDED, calls.calls.value.getValue("c1").status)
    }
}
