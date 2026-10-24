package com.simbridge.host.telephony

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.simbridge.core.model.Clock
import com.simbridge.core.model.DialPolicy
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.model.Redaction
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.CallCommand
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.protocol.DialRequest
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.storage.ledger.CommandLedger
import com.simbridge.core.storage.ledger.LedgerClaim
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.transport.BridgeSession
import com.simbridge.host.feature.permissions.HostPermissions
import com.simbridge.host.session.HostCommandHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote call control. A dial request is claimed in the ledger before Telecom is touched, so a retried
 * request (same idempotency key) is answered from the ledger and never places a second call.
 */
@Singleton
class CallCommandHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: CallRegistry,
    private val ledger: CommandLedger,
    private val clock: Clock,
) : HostCommandHandler {
    private val policy = DialPolicy()

    override val types = setOf(
        MessageType.CALL_DIAL_REQUEST,
        MessageType.CALL_ANSWER_REQUEST,
        MessageType.CALL_REJECT_REQUEST,
        MessageType.CALL_HANGUP_REQUEST,
    )

    override suspend fun handle(session: BridgeSession, envelope: Envelope) {
        if (envelope.type == MessageType.CALL_DIAL_REQUEST) return dial(session, envelope)
        val command = envelope.decodeBody(CallCommand.serializer())
        val result = when (envelope.type) {
            MessageType.CALL_ANSWER_REQUEST -> registry.answer(command.callId)
            MessageType.CALL_REJECT_REQUEST -> registry.reject(command.callId)
            else -> registry.hangup(command.callId)
        }
        when (result) {
            is CallCommandResult.Done -> ack(session, envelope, result.detail)
            CallCommandResult.UnknownCall -> error(session, envelope, ErrorCode.UNKNOWN_CALL, "That call is no longer on the Host")
            is CallCommandResult.InvalidState -> error(session, envelope, ErrorCode.TELEPHONY_FAILURE, result.message)
        }
    }

    override suspend fun onClientConnected(session: BridgeSession) {
        ledger.prune(clock.now() - LEDGER_RETENTION_MS)
        registry.liveCalls().forEach { session.send(MessageType.CALL_STATE, CallInfo.serializer(), it) }
    }

    override suspend fun onWipe() = ledger.clear()

    private suspend fun dial(session: BridgeSession, envelope: Envelope) {
        val request = envelope.decodeBody(DialRequest.serializer())
        val key = "dial:${request.idempotencyKey}"
        when (val claim = ledger.claim(key, "DIAL", clock.now())) {
            is LedgerClaim.Duplicate -> return replyFromOutcome(session, envelope, claim.outcome)
            LedgerClaim.New -> Unit
        }
        val outcome = place(request.number)
        ledger.complete(key, outcome)
        BridgeLog.i(TAG, "Dial ${Redaction.phone(request.number)} -> ${outcome.substringBefore('|')}")
        replyFromOutcome(session, envelope, outcome)
    }

    /** Returns "placed" or "error|CODE|message". */
    private fun place(number: String): String {
        val normalized = PhoneNumbers.normalize(number)
        val platformEmergency = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(normalized) == true
        }.getOrDefault(false)
        val verdict = if (platformEmergency) DialPolicy.Verdict.BLOCKED_EMERGENCY else policy.evaluate(number)
        when (verdict) {
            DialPolicy.Verdict.BLOCKED_EMERGENCY ->
                return failure(ErrorCode.BLOCKED_NUMBER, "Emergency calls cannot be placed through SIM Bridge. Use a phone with a SIM.")
            DialPolicy.Verdict.BLOCKED_PREMIUM -> return failure(ErrorCode.BLOCKED_NUMBER, "Premium-rate numbers are blocked")
            DialPolicy.Verdict.INVALID -> return failure(ErrorCode.INVALID_NUMBER, "That is not a valid phone number")
            DialPolicy.Verdict.ALLOWED -> Unit
        }
        if (!HostPermissions.isRoleHeld(context, RoleManager.ROLE_DIALER)) {
            return failure(ErrorCode.ROLE_MISSING, "SIM Bridge Host is not the default phone app on the Host phone")
        }
        if (!HostPermissions.granted(context, Manifest.permission.CALL_PHONE)) {
            return failure(ErrorCode.PERMISSION_MISSING, "Phone permission is not granted on the Host phone")
        }
        return try {
            context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", normalized, null), Bundle())
            OUTCOME_PLACED
        } catch (e: SecurityException) {
            failure(ErrorCode.PERMISSION_MISSING, "The Host refused to place the call")
        } catch (e: Exception) {
            failure(ErrorCode.TELEPHONY_FAILURE, "The Host could not place the call")
        }
    }

    private suspend fun replyFromOutcome(session: BridgeSession, envelope: Envelope, outcome: String?) {
        when {
            outcome == null -> error(
                session, envelope, ErrorCode.DUPLICATE_OUTCOME_UNKNOWN,
                "This call request is already being handled. It will not be dialled twice.",
            )
            outcome == OUTCOME_PLACED -> ack(session, envelope, OUTCOME_PLACED)
            else -> {
                val parts = outcome.split('|', limit = 3)
                val code = runCatching { ErrorCode.valueOf(parts[1]) }.getOrDefault(ErrorCode.TELEPHONY_FAILURE)
                error(session, envelope, code, parts.getOrElse(2) { "The call could not be placed" })
            }
        }
    }

    private fun failure(code: ErrorCode, message: String) = "error|${code.name}|$message"

    private suspend fun ack(session: BridgeSession, envelope: Envelope, detail: String) =
        session.send(MessageType.ACK, Ack.serializer(), Ack(detail), correlationId = envelope.messageId)

    private suspend fun error(session: BridgeSession, envelope: Envelope, code: ErrorCode, message: String) =
        session.send(MessageType.ERROR, ErrorBody.serializer(), ErrorBody(code, message), correlationId = envelope.messageId)

    private companion object {
        const val TAG = "CallCommands"
        const val OUTCOME_PLACED = "placed"
        const val LEDGER_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
