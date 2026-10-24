package com.simbridge.host.session

import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.transport.BridgeSession

/**
 * Feature handlers (SMS, calls) register with [HostBridge]. They reply on the session the
 * request arrived on and push events through [HostBridge.publish].
 */
interface HostCommandHandler {
    val types: Set<MessageType>

    suspend fun handle(session: BridgeSession, envelope: Envelope)

    /** Called after a Client authenticates, before it sends anything. Push current state here. */
    suspend fun onClientConnected(session: BridgeSession) = Unit

    /** The Host user unpaired everything or wiped data. */
    suspend fun onWipe() = Unit
}
