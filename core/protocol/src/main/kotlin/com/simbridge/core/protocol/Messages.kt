package com.simbridge.core.protocol

import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.model.DeviceRole
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import kotlinx.serialization.Serializable

@Serializable
data class Hello(
    val role: DeviceRole,
    val deviceName: String,
    val supportedVersions: List<Int>,
    val appVersion: String,
    val selectedVersion: Int? = null,
)

@Serializable
data class Capabilities(
    val calls: CapabilityStatus,
    val sms: CapabilityStatus,
    val audio: AudioStatus,
    val multiSim: Boolean = false,
)

@Serializable
data class PairRequest(val token: String, val deviceName: String)

@Serializable
data class PairConfirm(val approved: Boolean)

@Serializable
enum class AuthFailure { PAIRING_REQUIRED, PAIRING_EXPIRED, PAIRING_REJECTED, NOT_PAIRED, UNSUPPORTED_VERSION }

@Serializable
data class AuthResult(val ok: Boolean, val failure: AuthFailure? = null, val hostName: String? = null)

@Serializable
data class Heartbeat(val nowMs: Long)

/** Values the Host OS could not read are null, never guessed. */
@Serializable
data class HostStatus(
    val simReady: Boolean,
    val carrierName: String? = null,
    val signalLevel: Int? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
)

/** Body of both CALL_INCOMING and CALL_STATE. [callId] is always assigned by the Host. */
@Serializable
data class CallInfo(
    val callId: String,
    val number: String?,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAtMs: Long,
    val connectedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val disconnectReason: String? = null,
)

@Serializable
data class DialRequest(val idempotencyKey: String, val number: String)

/** Body of CALL_ANSWER_REQUEST, CALL_REJECT_REQUEST and CALL_HANGUP_REQUEST. */
@Serializable
data class CallCommand(val callId: String)

@Serializable
data class SmsSendRequest(val clientMessageId: String, val to: String, val text: String)

@Serializable
data class SmsMessage(
    val hostSmsId: Long,
    val clientMessageId: String? = null,
    val address: String,
    val body: String,
    val direction: SmsDirection,
    val status: SmsStatus,
    val timestampMs: Long,
    val errorCode: Int? = null,
)

@Serializable
data class SmsStatusUpdate(
    val hostSmsId: Long,
    val clientMessageId: String? = null,
    val status: SmsStatus,
    val errorCode: Int? = null,
)

@Serializable
data class SyncRequest(val smsCheckpoint: Long, val callCheckpoint: Long, val limit: Int = 200)

@Serializable
data class SyncDelta(
    val sms: List<SmsMessage>,
    val calls: List<CallInfo>,
    val smsCheckpoint: Long,
    val callCheckpoint: Long,
    val hasMore: Boolean,
)

@Serializable
data class ErrorBody(val code: ErrorCode, val message: String)

@Serializable
data class Ack(val detail: String? = null)

/** Either side revokes the pairing; the receiver forgets the sender's fingerprint. */
@Serializable
data class Unpair(val reason: String? = null)
