package com.simbridge.host.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.model.Clock
import com.simbridge.core.model.Redaction
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.host.data.HostDatabase
import com.simbridge.host.di.ApplicationScope
import com.simbridge.host.session.HostBridge
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CallCommandResult {
    data class Done(val detail: String) : CallCommandResult
    data object UnknownCall : CallCommandResult
    data class InvalidState(val message: String) : CallCommandResult
}

/**
 * Tracks every Telecom call on the Host, assigns the bridge call IDs (the Host owns them),
 * persists each change for sync and publishes it to the Client in order.
 */
@Singleton
class CallRegistry @Inject constructor(
    private val db: HostDatabase,
    private val bridge: HostBridge,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private class Tracked(val callId: String, val call: Call, var info: CallInfo, var announced: Boolean = false)

    private val byId = ConcurrentHashMap<String, Tracked>()
    private val idByCall = ConcurrentHashMap<Call, String>()
    private val outbox = Channel<Pair<CallInfo, Boolean>>(Channel.UNLIMITED)
    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    private var service = WeakReference<InCallService>(null)

    /** Live calls on this Host, oldest first. */
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    init {
        scope.launch {
            for ((info, incomingAnnouncement) in outbox) {
                persist(info)
                val type = if (incomingAnnouncement) MessageType.CALL_INCOMING else MessageType.CALL_STATE
                bridge.publish(type, CallInfo.serializer(), info)
            }
        }
    }

    fun attach(inCallService: InCallService) {
        service = WeakReference(inCallService)
    }

    fun detach(inCallService: InCallService) {
        if (service.get() === inCallService) service = WeakReference(null)
    }

    fun onCallAdded(call: Call) {
        val callId = UUID.randomUUID().toString()
        val details = call.details
        val initial = CallInfo(
            callId = callId,
            number = numberOf(call),
            direction = CallStateMapper.direction(details.callDirection),
            status = CallStatus.CONNECTING,
            startedAtMs = clock.now(),
        )
        idByCall[call] = callId
        byId[callId] = Tracked(callId, call, initial)
        call.registerCallback(callback)
        BridgeLog.i(TAG, "Call added ${initial.direction} ${Redaction.phone(initial.number)}")
        update(call, force = true)
    }

    fun onCallRemoved(call: Call) {
        update(call, force = false)
        call.unregisterCallback(callback)
        idByCall.remove(call)?.let { byId.remove(it) }
        refresh()
    }

    fun liveCalls(): List<CallInfo> = byId.values.map { it.info }.filter { it.status.isLive }

    fun answer(callId: String): CallCommandResult = withCall(callId) { call, info ->
        when (info.status) {
            CallStatus.RINGING -> {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                CallCommandResult.Done("answering")
            }
            CallStatus.ACTIVE -> CallCommandResult.Done("already active")
            else -> CallCommandResult.InvalidState("Call is ${info.status.name.lowercase()}, it cannot be answered")
        }
    }

    fun reject(callId: String): CallCommandResult = withCall(callId) { call, info ->
        when {
            info.status == CallStatus.RINGING -> {
                call.reject(false, null)
                CallCommandResult.Done("rejecting")
            }
            info.status.isTerminal -> CallCommandResult.Done("already ended")
            else -> {
                call.disconnect()
                CallCommandResult.Done("ending")
            }
        }
    }

    fun hangup(callId: String): CallCommandResult = withCall(callId) { call, info ->
        if (info.status.isTerminal) {
            CallCommandResult.Done("already ended")
        } else {
            call.disconnect()
            CallCommandResult.Done("ending")
        }
    }

    /** Host-local audio controls for the Host's own in-call screen. */
    fun setMuted(muted: Boolean) = service.get()?.setMuted(muted)

    fun setSpeaker(on: Boolean) =
        service.get()?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)

    @Suppress("DEPRECATION")
    fun audioState(): CallAudioState? = service.get()?.callAudioState

    private inline fun withCall(callId: String, action: (Call, CallInfo) -> CallCommandResult): CallCommandResult {
        val tracked = byId[callId] ?: return CallCommandResult.UnknownCall
        return runCatching { action(tracked.call, tracked.info) }
            .getOrElse { CallCommandResult.InvalidState(it.message ?: "Telecom refused the command") }
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = update(call, force = false)
        override fun onDetailsChanged(call: Call, details: Call.Details) = update(call, force = false)
    }

    private fun update(call: Call, force: Boolean) {
        val callId = idByCall[call] ?: return
        val tracked = byId[callId] ?: return
        val previous = tracked.info
        val details = call.details
        val disconnectCode = details.disconnectCause?.code
        val status = CallStateMapper.status(stateOf(call), disconnectCode)
        if (previous.status.isTerminal && !force) return
        val now = clock.now()
        val next = previous.copy(
            number = numberOf(call) ?: previous.number,
            status = status,
            connectedAtMs = previous.connectedAtMs ?: details.connectTimeMillis.takeIf { it > 0 && status == CallStatus.ACTIVE }
                ?: if (status == CallStatus.ACTIVE) now else null,
            endedAtMs = if (status.isTerminal) previous.endedAtMs ?: now else null,
            disconnectReason = if (status.isTerminal) CallStateMapper.reason(disconnectCode) else null,
        )
        if (next == previous && !force) return
        if (next.status != previous.status && !previous.status.canTransitionTo(next.status)) {
            BridgeLog.w(TAG, "Unusual transition ${previous.status} -> ${next.status}; Host state wins")
        }
        tracked.info = next
        val announce = next.direction == CallDirection.INCOMING && next.status == CallStatus.RINGING && !tracked.announced
        if (announce) tracked.announced = true
        refresh()
        outbox.trySend(next to announce)
    }

    private fun refresh() {
        _calls.value = byId.values.map { it.info }.filter { !it.status.isTerminal }.sortedBy { it.startedAtMs }
    }

    private suspend fun persist(info: CallInfo) {
        db.calls().upsert(
            CallRecordEntity(
                callId = info.callId,
                number = info.number,
                direction = info.direction,
                status = info.status,
                startedAtMs = info.startedAtMs,
                connectedAtMs = info.connectedAtMs,
                endedAtMs = info.endedAtMs,
                disconnectReason = info.disconnectReason,
                changeSeq = db.syncClock().next(),
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

    private fun numberOf(call: Call): String? {
        val details = call.details
        if (details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED) return null
        return details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "CallRegistry"
    }
}
