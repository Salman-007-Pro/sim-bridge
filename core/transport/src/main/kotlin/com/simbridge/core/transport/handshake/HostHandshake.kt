package com.simbridge.core.transport.handshake

import com.simbridge.core.crypto.PairingWindow
import com.simbridge.core.crypto.Sas
import com.simbridge.core.model.DeviceRole
import com.simbridge.core.protocol.AuthFailure
import com.simbridge.core.protocol.AuthResult
import com.simbridge.core.protocol.Hello
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.PairConfirm
import com.simbridge.core.protocol.PairRequest
import com.simbridge.core.protocol.ProtocolVersion
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.transport.BridgeSession
import com.simbridge.core.transport.CloseReason
import kotlinx.coroutines.withTimeoutOrNull

interface PairedClientRegistry {
    suspend fun isPaired(fingerprint: String): Boolean
    suspend fun register(fingerprint: String, deviceName: String)
}

data class PairingApprovalRequest(val clientName: String, val clientFingerprint: String, val sas: String)

/** Shown to the Host user; returns true only on explicit approval. */
fun interface PairingApprover {
    suspend fun approve(request: PairingApprovalRequest): Boolean
}

sealed interface HostHandshakeResult {
    data class Authenticated(val clientName: String, val newlyPaired: Boolean, val version: Int) : HostHandshakeResult
    data class Rejected(val failure: AuthFailure?, val detail: String) : HostHandshakeResult
}

/**
 * Host side of the application handshake run on every new TLS session:
 * HELLO / version negotiation, then either a known paired fingerprint or the
 * one-time-token + SAS approval flow. Anything else closes the session.
 */
class HostHandshake(
    private val hostName: String,
    private val hostFingerprint: String,
    private val appVersion: String,
    private val registry: PairedClientRegistry,
    private val pairingWindow: PairingWindow,
    private val approver: PairingApprover,
    private val clock: () -> Long = System::currentTimeMillis,
    private val stepTimeoutMs: Long = 10_000,
    private val approvalTimeoutMs: Long = 120_000,
) {
    suspend fun run(session: BridgeSession): HostHandshakeResult {
        val first = session.receive(stepTimeoutMs) ?: return reject(session, null, "No HELLO received")
        if (first.type != MessageType.HELLO) return reject(session, null, "Expected HELLO")
        val hello = first.decodeBody(Hello.serializer())
        if (hello.role != DeviceRole.CLIENT) return reject(session, null, "Peer is not a Client")
        val version = ProtocolVersion.negotiate(ProtocolVersion.SUPPORTED, hello.supportedVersions)
            ?: return reject(session, AuthFailure.UNSUPPORTED_VERSION, "No common protocol version")

        session.send(
            MessageType.HELLO,
            Hello.serializer(),
            Hello(DeviceRole.HOST, hostName, ProtocolVersion.SUPPORTED.toList(), appVersion, selectedVersion = version),
            correlationId = first.messageId,
        )

        if (registry.isPaired(session.peerFingerprint)) {
            session.send(MessageType.AUTH_RESULT, AuthResult.serializer(), AuthResult(ok = true, hostName = hostName))
            return HostHandshakeResult.Authenticated(hello.deviceName, newlyPaired = false, version = version)
        }
        if (!pairingWindow.isOpen(clock())) return reject(session, AuthFailure.NOT_PAIRED, "Unknown Client")

        session.send(
            MessageType.AUTH_RESULT,
            AuthResult.serializer(),
            AuthResult(ok = false, failure = AuthFailure.PAIRING_REQUIRED, hostName = hostName),
        )
        val requestEnvelope = session.receive(stepTimeoutMs)
            ?: return reject(session, AuthFailure.PAIRING_EXPIRED, "No pairing request")
        if (requestEnvelope.type != MessageType.PAIR_REQUEST) return reject(session, null, "Expected PAIR_REQUEST")
        val request = requestEnvelope.decodeBody(PairRequest.serializer())

        when (pairingWindow.consume(request.token, clock())) {
            PairingWindow.ConsumeResult.ACCEPTED -> Unit
            PairingWindow.ConsumeResult.EXPIRED,
            PairingWindow.ConsumeResult.CLOSED -> return reject(session, AuthFailure.PAIRING_EXPIRED, "Pairing code expired")
            PairingWindow.ConsumeResult.INVALID -> return reject(session, AuthFailure.PAIRING_REJECTED, "Wrong pairing code")
        }

        val clientName = sanitizeName(request.deviceName)
        val sas = Sas.compute(request.token, hostFingerprint, session.peerFingerprint)
        val approved = withTimeoutOrNull(approvalTimeoutMs) {
            approver.approve(PairingApprovalRequest(clientName, session.peerFingerprint, sas))
        } == true

        session.send(MessageType.PAIR_CONFIRM, PairConfirm.serializer(), PairConfirm(approved))
        if (!approved) return reject(session, AuthFailure.PAIRING_REJECTED, "Pairing was not approved on the Host")

        registry.register(session.peerFingerprint, clientName)
        session.send(MessageType.AUTH_RESULT, AuthResult.serializer(), AuthResult(ok = true, hostName = hostName))
        return HostHandshakeResult.Authenticated(clientName, newlyPaired = true, version = version)
    }

    private suspend fun reject(session: BridgeSession, failure: AuthFailure?, detail: String): HostHandshakeResult.Rejected {
        runCatching {
            session.send(MessageType.AUTH_RESULT, AuthResult.serializer(), AuthResult(ok = false, failure = failure, hostName = hostName))
        }
        session.close(CloseReason.AUTH_FAILED)
        return HostHandshakeResult.Rejected(failure, detail)
    }

    companion object {
        fun sanitizeName(raw: String): String =
            raw.filter { !it.isISOControl() }.trim().take(40).ifBlank { "Client phone" }
    }
}
