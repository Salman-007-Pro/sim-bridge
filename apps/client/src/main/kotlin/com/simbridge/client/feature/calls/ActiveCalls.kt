package com.simbridge.client.feature.calls

import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live projection of the Host's calls. Ended calls stay briefly so the UI can show the outcome. */
@Singleton
class ActiveCalls @Inject constructor() {
    private val _calls = MutableStateFlow<Map<String, CallInfo>>(emptyMap())
    val calls: StateFlow<Map<String, CallInfo>> = _calls.asStateFlow()

    /** Returns the stored value; a final state is never replaced by a late non-final one. */
    fun apply(info: CallInfo): CallInfo {
        var stored = info
        _calls.update { current ->
            val existing = current[info.callId]
            stored = if (existing != null && existing.status.isTerminal && !info.status.isTerminal) existing else info
            current + (info.callId to stored)
        }
        return stored
    }

    fun remove(callId: String) = _calls.update { it - callId }

    /** The link dropped: the Host still owns these calls, but this phone can no longer see or control them. */
    fun markUnreachable() = _calls.update { current ->
        current.mapValues { (_, call) -> if (call.status.isLive) call.copy(status = CallStatus.HOST_UNREACHABLE) else call }
    }

    fun clear() = _calls.update { emptyMap() }

    companion object {
        /** The call the UI should focus on: ringing first, then the newest live call, then the newest ended one. */
        fun primary(calls: Collection<CallInfo>): CallInfo? =
            calls.firstOrNull { it.status == CallStatus.RINGING }
                ?: calls.filter { it.status.isLive }.maxByOrNull { it.startedAtMs }
                ?: calls.maxByOrNull { it.startedAtMs }
    }
}
