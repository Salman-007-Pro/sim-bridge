package com.simbridge.client.session

import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.MessageType
import kotlinx.serialization.KSerializer

sealed interface RequestOutcome {
    data class Acked(val reply: Envelope) : RequestOutcome
    data class Rejected(val error: ErrorBody) : RequestOutcome

    /** The request may or may not have reached the Host. Never retry non-idempotent commands on this. */
    data object Timeout : RequestOutcome
    data object NotConnected : RequestOutcome
}

/** The Client's view of the current Host session. Requests are commands; the Host's reply is authoritative. */
interface HostLink {
    suspend fun <T> request(
        type: MessageType,
        serializer: KSerializer<T>,
        body: T,
        timeoutMs: Long = 10_000,
    ): RequestOutcome

    suspend fun <T> send(type: MessageType, serializer: KSerializer<T>, body: T): Boolean
}

/** Feature handlers (messages, calls) register with [ClientBridge]. */
interface ClientMessageHandler {
    val types: Set<MessageType>

    suspend fun handle(envelope: Envelope)

    /** Runs after authentication on every (re)connect: sync, then flush safe queued work. */
    suspend fun onConnected(link: HostLink) = Unit

    suspend fun onDisconnected() = Unit

    /** Delete everything this feature stored locally. */
    suspend fun onWipe() = Unit
}
