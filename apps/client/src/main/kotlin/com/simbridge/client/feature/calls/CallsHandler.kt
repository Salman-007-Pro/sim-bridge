package com.simbridge.client.feature.calls

import com.simbridge.client.di.ApplicationScope
import com.simbridge.client.feature.calls.data.CallHistoryRepository
import com.simbridge.client.session.ClientMessageHandler
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.decodeBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Applies the Host's call events to the live store, history and notifications. */
@Singleton
class CallsHandler @Inject constructor(
    private val active: ActiveCalls,
    private val history: CallHistoryRepository,
    private val controller: CallController,
    private val notifier: CallNotifier,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ClientMessageHandler {
    override val types = setOf(MessageType.CALL_INCOMING, MessageType.CALL_STATE)

    init {
        scope.launch {
            active.calls.map { ActiveCalls.primary(it.values) }.distinctUntilChanged().collect(notifier::render)
        }
    }

    override suspend fun handle(envelope: Envelope) {
        val info = active.apply(envelope.decodeBody(CallInfo.serializer()))
        controller.onCallState(info)
        val stored = history.apply(info)
        if (info.status.isTerminal) {
            if (stored.missed) notifier.missed(info)
            scope.launch {
                delay(ENDED_LINGER_MS)
                if (active.calls.value[info.callId]?.status?.isTerminal == true) active.remove(info.callId)
            }
        }
    }

    override suspend fun onDisconnected() = active.markUnreachable()

    /** The Host re-announces its live calls on connect; anything still unreachable afterwards has ended. */
    override suspend fun onConnected(link: com.simbridge.client.session.HostLink) {
        scope.launch {
            delay(STALE_AFTER_RECONNECT_MS)
            active.calls.value.values
                .filter { it.status == com.simbridge.core.model.CallStatus.HOST_UNREACHABLE }
                .forEach { active.remove(it.callId) }
        }
    }

    override suspend fun onWipe() {
        active.clear()
        history.clear()
    }

    private companion object {
        const val ENDED_LINGER_MS = 3_000L
        const val STALE_AFTER_RECONNECT_MS = 5_000L
    }
}
