package com.simbridge.core.protocol

import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.model.DeviceRole
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtocolCodecTest {
    private val codec = ProtocolCodec()

    private val call = CallInfo("c1", "+923001234567", CallDirection.INCOMING, CallStatus.RINGING, 1_000)
    private val sms = SmsMessage(7, "m1", "+923001234567", "Salam ✓ 🙂", SmsDirection.OUTBOUND, SmsStatus.SENT, 2_000)

    private val samples: Map<MessageType, Pair<KSerializer<*>, Any>> = mapOf(
        MessageType.HELLO to (Hello.serializer() to Hello(DeviceRole.CLIENT, "Pixel", listOf(1), "0.1.0")),
        MessageType.CAPABILITIES to (Capabilities.serializer() to
            Capabilities(CapabilityStatus.SUPPORTED, CapabilityStatus.NEEDS_SETUP, AudioStatus.UNSUPPORTED)),
        MessageType.PAIR_REQUEST to (PairRequest.serializer() to PairRequest("tok", "Client")),
        MessageType.PAIR_CONFIRM to (PairConfirm.serializer() to PairConfirm(true)),
        MessageType.AUTH_RESULT to (AuthResult.serializer() to AuthResult(false, AuthFailure.PAIRING_REQUIRED)),
        MessageType.HEARTBEAT to (Heartbeat.serializer() to Heartbeat(5)),
        MessageType.HOST_STATUS to (HostStatus.serializer() to HostStatus(true, "Jazz", 3, 80, true)),
        MessageType.CALL_INCOMING to (CallInfo.serializer() to call),
        MessageType.CALL_STATE to (CallInfo.serializer() to call.copy(status = CallStatus.ACTIVE)),
        MessageType.CALL_DIAL_REQUEST to (DialRequest.serializer() to DialRequest("k1", "03001234567")),
        MessageType.CALL_ANSWER_REQUEST to (CallCommand.serializer() to CallCommand("c1")),
        MessageType.CALL_REJECT_REQUEST to (CallCommand.serializer() to CallCommand("c1")),
        MessageType.CALL_HANGUP_REQUEST to (CallCommand.serializer() to CallCommand("c1")),
        MessageType.SMS_SEND_REQUEST to (SmsSendRequest.serializer() to SmsSendRequest("m1", "0300", "hi")),
        MessageType.SMS_RECEIVED to (SmsMessage.serializer() to sms),
        MessageType.SMS_STATUS to (SmsStatusUpdate.serializer() to SmsStatusUpdate(7, "m1", SmsStatus.DELIVERED)),
        MessageType.SYNC_REQUEST to (SyncRequest.serializer() to SyncRequest(0, 0)),
        MessageType.SYNC_DELTA to (SyncDelta.serializer() to SyncDelta(listOf(sms), listOf(call), 9, 4, false)),
        MessageType.ERROR to (ErrorBody.serializer() to ErrorBody(ErrorCode.REPLAY, "dup")),
        MessageType.ACK to (Ack.serializer() to Ack()),
        MessageType.UNPAIR to (Unpair.serializer() to Unpair("user")),
    )

    @Suppress("UNCHECKED_CAST")
    private fun envelope(type: MessageType, seq: Long = 1): Envelope {
        val (serializer, body) = samples.getValue(type)
        return Envelope(
            protocolVersion = 1,
            messageId = "id-$type",
            sessionId = "s1",
            seq = seq,
            sentAtEpochMs = 10,
            type = type,
            body = ProtocolCodec.encodeBody(type, serializer as KSerializer<Any>, body),
        )
    }

    @Test
    fun everyMessageTypeHasASampleAndRoundTrips() {
        assertEquals(MessageType.entries.toSet(), samples.keys)
        for (type in MessageType.entries) {
            val original = envelope(type)
            val result = codec.decode(codec.encode(original))
            assertTrue("$type should decode", result is DecodeResult.Ok)
            assertEquals(original, (result as DecodeResult.Ok).envelope)
            @Suppress("UNCHECKED_CAST")
            val decodedBody = result.envelope.decodeBody(samples.getValue(type).first as KSerializer<Any>)
            assertEquals(samples.getValue(type).second, decodedBody)
        }
    }

    @Test
    fun rejectsBodyThatDoesNotMatchType() {
        val wrong = envelope(MessageType.HEARTBEAT).copy(type = MessageType.CALL_DIAL_REQUEST)
        val result = codec.decode(codec.encode(wrong))
        assertRejected(result, ErrorCode.MALFORMED)
        assertEquals("id-HEARTBEAT", (result as DecodeResult.Rejected).messageId)
    }

    @Test
    fun rejectsUnsupportedVersionGracefully() {
        val future = envelope(MessageType.ACK).copy(protocolVersion = 99)
        assertRejected(codec.decode(codec.encode(future)), ErrorCode.UNSUPPORTED_VERSION)
    }

    @Test
    fun rejectsUnknownTypeAndUnknownKeys() {
        val base = String(codec.encode(envelope(MessageType.ACK)))
        assertRejected(codec.decode(base.replace("\"ACK\"", "\"TELEPORT\"").toByteArray()), ErrorCode.MALFORMED)
        assertRejected(codec.decode(base.replace("{\"protocolVersion\"", "{\"evil\":1,\"protocolVersion\"").toByteArray()), ErrorCode.MALFORMED)
    }

    @Test
    fun rejectsInvalidIdentifiersAndSequence() {
        assertRejected(codec.decode(codec.encode(envelope(MessageType.ACK, seq = 0))), ErrorCode.MALFORMED)
        val longId = envelope(MessageType.ACK).copy(messageId = "x".repeat(65))
        assertRejected(codec.decode(codec.encode(longId)), ErrorCode.MALFORMED)
        val blankSession = envelope(MessageType.ACK).copy(sessionId = "")
        assertRejected(codec.decode(codec.encode(blankSession)), ErrorCode.MALFORMED)
    }

    @Test
    fun rejectsMissingRequiredBodyFields() {
        val env = envelope(MessageType.CALL_DIAL_REQUEST).copy(body = buildJsonObject { put("number", "0300") })
        assertRejected(codec.decode(codec.encode(env)), ErrorCode.MALFORMED)
    }

    @Test
    fun fuzzedInputNeverThrows() {
        val random = Random(42)
        val valid = codec.encode(envelope(MessageType.SYNC_DELTA))
        repeat(5_000) { i ->
            val bytes = if (i % 2 == 0) {
                random.nextBytes(random.nextInt(0, 512))
            } else {
                valid.copyOf().also { b -> repeat(random.nextInt(1, 8)) { b[random.nextInt(b.size)] = random.nextInt().toByte() } }
            }
            try {
                codec.decode(bytes)
            } catch (t: Throwable) {
                fail("decode threw ${t::class.simpleName} on iteration $i")
            }
        }
    }

    @Test
    fun encodeBodyRefusesMismatchedSchema() {
        try {
            ProtocolCodec.encodeBody(MessageType.CALL_DIAL_REQUEST, Heartbeat.serializer(), Heartbeat(1))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun negotiatesHighestCommonVersion() {
        assertEquals(2, ProtocolVersion.negotiate(setOf(1, 2), listOf(2, 3)))
        assertEquals(1, ProtocolVersion.negotiate(setOf(1), listOf(1)))
        assertNull(ProtocolVersion.negotiate(setOf(1), listOf(2, 3)))
        assertNull(ProtocolVersion.negotiate(setOf(1), emptyList()))
    }

    @Test
    fun sequenceGuardRejectsReplayAndReorder() {
        val guard = SequenceGuard()
        assertTrue(guard.accept(1))
        assertTrue(guard.accept(2))
        assertFalse(guard.accept(2))
        assertFalse(guard.accept(1))
        assertTrue(guard.accept(5))
        assertFalse(guard.accept(4))
    }

    @Test
    fun emptyBodyObjectIsValidForAck() {
        val env = envelope(MessageType.ACK).copy(body = JsonObject(mapOf("detail" to JsonPrimitive("ok"))))
        assertTrue(codec.decode(codec.encode(env)) is DecodeResult.Ok)
        val bare = envelope(MessageType.ACK).copy(body = JsonObject(emptyMap()))
        assertTrue(codec.decode(codec.encode(bare)) is DecodeResult.Ok)
    }

    private fun assertRejected(result: DecodeResult, code: ErrorCode) {
        assertTrue("Expected rejection but got $result", result is DecodeResult.Rejected)
        assertEquals(code, (result as DecodeResult.Rejected).code)
    }
}
