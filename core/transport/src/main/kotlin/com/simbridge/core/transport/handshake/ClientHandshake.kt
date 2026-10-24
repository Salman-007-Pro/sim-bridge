package com.simbridge.core.transport.handshake

import com.simbridge.core.crypto.Sas
import com.simbridge.core.model.DeviceRole
import com.simbridge.core.protocol.AuthFailure
import com.simbridge.core.protocol.AuthResult
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.Hello
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.PairConfirm
import com.simbridge.core.protocol.PairRequest
import com.simbridge.core.protocol.ProtocolVersion
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.transport.BridgeSession
import com.simbridge.core.transport.CloseReason

sealed interface ClientAuthResult {
    data class Authenticated(val hostName: String, val version: Int) : ClientAuthResult
    data class PairingRequired(val hostName: String, val version: Int) : ClientAuthResult
    data class Failed(val failure: AuthFailure?, val detail: String) : ClientAuthResult
}

/** Client side of the handshake. The TLS layer has already verified the pinned Host certificate. */
class ClientHandshake(
    private val deviceName: String,
    private val appVersion: String,
    private val ownFingerprint: String,
    private val stepTimeoutMs: Long = 10_000,
) {
    suspend fun hello(session: BridgeSession): ClientAuthResult {
        session.send(
            MessageType.HELLO,
            Hello.serializer(),
            Hello(DeviceRole.CLIENT, deviceName, ProtocolVersion.SUPPORTED.toList(), appVersion),
        )
        val reply = session.receive(stepTimeoutMs) ?: return fail(session, null, "Host did not answer")
        if (reply.type == MessageType.AUTH_RESULT) return authFailure(session, reply)
        if (reply.type != MessageType.HELLO) return fail(session, null, "Unexpected ${reply.type}")
        val hostHello = reply.decodeBody(Hello.serializer())
        val version = hostHello.selectedVersion?.takeIf { it in ProtocolVersion.SUPPORTED }
            ?: return fail(session, AuthFailure.UNSUPPORTED_VERSION, "Host chose an unsupported version")

        val authEnvelope = session.receive(stepTimeoutMs) ?: return fail(session, null, "No AUTH_RESULT")
        if (authEnvelope.type != MessageType.AUTH_RESULT) return fail(session, null, "Unexpected ${authEnvelope.type}")
        val auth = authEnvelope.decodeBody(AuthResult.serializer())
        val hostName = auth.hostName ?: hostHello.deviceName
        return when {
            auth.ok -> ClientAuthResult.Authenticated(hostName, version)
            auth.failure == AuthFailure.PAIRING_REQUIRED -> ClientAuthResult.PairingRequired(hostName, version)
            else -> fail(session, auth.failure, "Host refused the connection")
        }
    }

    /**
     * Continues after [hello] returned [ClientAuthResult.PairingRequired]. [onSas] receives the
     * verification code to display; this suspends until the Host user approves or rejects.
     */
    suspend fun pair(
        session: BridgeSession,
        token: String,
        onSas: (String) -> Unit,
        approvalTimeoutMs: Long = 150_000,
    ): ClientAuthResult {
        onSas(Sas.compute(token, session.peerFingerprint, ownFingerprint))
        session.send(MessageType.PAIR_REQUEST, PairRequest.serializer(), PairRequest(token, deviceName))
        val confirm = session.receive(approvalTimeoutMs)
            ?: return fail(session, AuthFailure.PAIRING_EXPIRED, "No answer from the Host")
        if (confirm.type == MessageType.AUTH_RESULT) return authFailure(session, confirm)
        if (confirm.type != MessageType.PAIR_CONFIRM) return fail(session, null, "Unexpected ${confirm.type}")
        if (!confirm.decodeBody(PairConfirm.serializer()).approved) {
            return fail(session, AuthFailure.PAIRING_REJECTED, "Pairing was rejected on the Host")
        }
        val authEnvelope = session.receive(stepTimeoutMs) ?: return fail(session, null, "No AUTH_RESULT")
        val auth = authEnvelope.takeIf { it.type == MessageType.AUTH_RESULT }?.decodeBody(AuthResult.serializer())
            ?: return fail(session, null, "Unexpected ${authEnvelope.type}")
        return if (auth.ok) {
            ClientAuthResult.Authenticated(auth.hostName ?: "Host phone", ProtocolVersion.CURRENT)
        } else {
            fail(session, auth.failure, "Host refused the pairing")
        }
    }

    private fun authFailure(session: BridgeSession, envelope: Envelope): ClientAuthResult.Failed {
        val auth = envelope.decodeBody(AuthResult.serializer())
        return fail(session, auth.failure, "Host refused the connection")
    }

    private fun fail(session: BridgeSession, failure: AuthFailure?, detail: String): ClientAuthResult.Failed {
        session.close(CloseReason.AUTH_FAILED)
        return ClientAuthResult.Failed(failure, detail)
    }
}
