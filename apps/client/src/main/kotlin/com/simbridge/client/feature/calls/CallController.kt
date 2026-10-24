package com.simbridge.client.feature.calls

import com.simbridge.client.session.HostLink
import com.simbridge.client.session.RequestOutcome
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.DialPolicy
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.protocol.CallCommand
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.protocol.DialRequest
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.MessageType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DialState { REQUESTING, PLACED, FAILED, UNKNOWN }

data class DialAttempt(
    val idempotencyKey: String,
    val number: String,
    val state: DialState,
    val message: String? = null,
)

/**
 * Sends call commands to the Host. A dial is never retried automatically: after an unknown outcome
 * the user can only re-check with the *same* idempotency key, which the Host answers from its ledger.
 */
@Singleton
class CallController @Inject constructor(private val bridge: HostLink) {
    private val policy = DialPolicy()
    private val _dial = MutableStateFlow<DialAttempt?>(null)
    val dial: StateFlow<DialAttempt?> = _dial.asStateFlow()

    fun validate(number: String): String? = when (policy.evaluate(number)) {
        DialPolicy.Verdict.BLOCKED_EMERGENCY ->
            "Emergency calls are not routed through SIM Bridge. Call from a phone with a SIM, such as the Host phone."
        DialPolicy.Verdict.BLOCKED_PREMIUM -> "Premium-rate numbers are blocked."
        DialPolicy.Verdict.INVALID -> "Enter a valid phone number."
        DialPolicy.Verdict.ALLOWED -> null
    }

    suspend fun dial(number: String): DialAttempt {
        val normalized = PhoneNumbers.normalize(number)
        validate(number)?.let { reason ->
            return DialAttempt(UUID.randomUUID().toString(), normalized, DialState.FAILED, reason).also { _dial.value = it }
        }
        return send(DialAttempt(UUID.randomUUID().toString(), normalized, DialState.REQUESTING))
    }

    /** Asks the Host again about the same request. Safe: the Host never dials twice for one key. */
    suspend fun recheck(): DialAttempt? {
        val attempt = _dial.value ?: return null
        return send(attempt.copy(state = DialState.REQUESTING, message = null))
    }

    fun onCallState(info: CallInfo) {
        val attempt = _dial.value ?: return
        if (info.direction == CallDirection.OUTGOING && PhoneNumbers.normalize(info.number.orEmpty()) == attempt.number) {
            _dial.value = null
        }
    }

    fun clearDial() {
        _dial.value = null
    }

    suspend fun answer(callId: String) = command(MessageType.CALL_ANSWER_REQUEST, callId)
    suspend fun reject(callId: String) = command(MessageType.CALL_REJECT_REQUEST, callId)
    suspend fun hangup(callId: String) = command(MessageType.CALL_HANGUP_REQUEST, callId)

    private suspend fun send(attempt: DialAttempt): DialAttempt {
        _dial.value = attempt
        val outcome = bridge.request(
            MessageType.CALL_DIAL_REQUEST,
            DialRequest.serializer(),
            DialRequest(attempt.idempotencyKey, attempt.number),
            timeoutMs = DIAL_TIMEOUT_MS,
        )
        val next = when (outcome) {
            is RequestOutcome.Acked -> attempt.copy(state = DialState.PLACED, message = "Calling via your Host phone…")
            is RequestOutcome.Rejected -> if (outcome.error.code == ErrorCode.DUPLICATE_OUTCOME_UNKNOWN) {
                attempt.copy(state = DialState.UNKNOWN, message = outcome.error.message)
            } else {
                attempt.copy(state = DialState.FAILED, message = outcome.error.message)
            }
            RequestOutcome.Timeout -> attempt.copy(
                state = DialState.UNKNOWN,
                message = "No reply from the Host. The call may have started — check the Host phone before trying again.",
            )
            RequestOutcome.NotConnected -> attempt.copy(state = DialState.FAILED, message = "Your Host phone is offline.")
        }
        if (_dial.value?.idempotencyKey == attempt.idempotencyKey) _dial.value = next
        return next
    }

    /** Returns an error message for the UI, or null on success. */
    private suspend fun command(type: MessageType, callId: String): String? =
        when (val outcome = bridge.request(type, CallCommand.serializer(), CallCommand(callId), timeoutMs = COMMAND_TIMEOUT_MS)) {
            is RequestOutcome.Acked -> null
            is RequestOutcome.Rejected -> outcome.error.message
            RequestOutcome.Timeout -> "The Host did not confirm. Check the Host phone."
            RequestOutcome.NotConnected -> "Your Host phone is offline."
        }

    private companion object {
        const val DIAL_TIMEOUT_MS = 12_000L
        const val COMMAND_TIMEOUT_MS = 8_000L
    }
}
