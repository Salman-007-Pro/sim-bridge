package com.simbridge.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object ProtocolVersion {
    const val CURRENT = 1
    val SUPPORTED: Set<Int> = setOf(1)

    /** Highest version both sides speak, or null if there is no overlap. */
    fun negotiate(local: Set<Int>, remote: Collection<Int>): Int? = local.intersect(remote.toSet()).maxOrNull()
}

/**
 * Every frame on the wire. [seq] is strictly increasing per sender per session;
 * [correlationId] links a reply (ACK / ERROR / state) to the request's [messageId].
 */
@Serializable
data class Envelope(
    val protocolVersion: Int,
    val messageId: String,
    val sessionId: String,
    val seq: Long,
    val sentAtEpochMs: Long,
    val type: MessageType,
    val correlationId: String? = null,
    val body: JsonObject = JsonObject(emptyMap()),
)

@Serializable
enum class MessageType {
    HELLO,
    CAPABILITIES,
    PAIR_REQUEST,
    PAIR_CONFIRM,
    AUTH_RESULT,
    HEARTBEAT,
    HOST_STATUS,
    CALL_INCOMING,
    CALL_STATE,
    CALL_DIAL_REQUEST,
    CALL_ANSWER_REQUEST,
    CALL_REJECT_REQUEST,
    CALL_HANGUP_REQUEST,
    SMS_SEND_REQUEST,
    SMS_RECEIVED,
    SMS_STATUS,
    SYNC_REQUEST,
    SYNC_DELTA,
    ERROR,
    ACK,
    UNPAIR,
}

@Serializable
enum class ErrorCode {
    MALFORMED,
    UNSUPPORTED_VERSION,
    UNAUTHORIZED,
    REPLAY,
    SESSION_MISMATCH,
    UNKNOWN_CALL,
    PERMISSION_MISSING,
    ROLE_MISSING,
    BLOCKED_NUMBER,
    INVALID_NUMBER,
    DUPLICATE_OUTCOME_UNKNOWN,
    TELEPHONY_FAILURE,
    NOT_SUPPORTED,
    INTERNAL,
}
