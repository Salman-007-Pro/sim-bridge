package com.simbridge.core.testing

import com.simbridge.core.crypto.PairingPayload
import com.simbridge.core.crypto.PairingWindow
import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.crypto.TlsIdentity
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.transport.BridgeServer
import com.simbridge.core.transport.BridgeSession
import com.simbridge.core.transport.SessionConfig
import com.simbridge.core.transport.handshake.HostHandshake
import com.simbridge.core.transport.handshake.HostHandshakeResult
import com.simbridge.core.transport.handshake.PairedClientRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

class InMemoryPairedClientRegistry : PairedClientRegistry {
    val clients = ConcurrentHashMap<String, String>()
    override suspend fun isPaired(fingerprint: String) = clients.containsKey(fingerprint)
    override suspend fun register(fingerprint: String, deviceName: String) {
        clients[fingerprint] = deviceName
    }
}

/**
 * Test simulator for the Host side. It runs the real TLS server and handshake; only the telephony
 * behaviour is scripted. It never claims calls or audio succeeded on its own: tests drive every event.
 */
class FakeHost(
    private val scope: CoroutineScope,
    val identity: TlsIdentity = TestIdentities.create("FakeHost"),
    private val sessionConfig: SessionConfig = SessionConfig(),
    var clock: () -> Long = System::currentTimeMillis,
) {
    val registry = InMemoryPairedClientRegistry()
    val pairingWindow = PairingWindow()
    val received = Channel<Envelope>(Channel.UNLIMITED)
    var approvePairing: Boolean = true
    var lastSas: String? = null
        private set

    /** Default behaviour ACKs every *_REQUEST; tests may replace it. */
    var onCommand: suspend (BridgeSession, Envelope) -> Unit = { session, envelope ->
        if (envelope.type.name.endsWith("_REQUEST") && envelope.type != MessageType.SYNC_REQUEST) {
            session.send(MessageType.ACK, Ack.serializer(), Ack(), correlationId = envelope.messageId)
        }
    }

    private val _session = MutableStateFlow<BridgeSession?>(null)
    val session: StateFlow<BridgeSession?> = _session

    private val server = BridgeServer(
        sslContext = { TlsContexts.create(identity, PinnedTrustManager(allowAnyPeer = true)) },
        scope = scope,
        preferredPort = 0,
        sessionConfig = sessionConfig,
    ) { session -> handle(session) }

    val port: Int get() = server.port ?: error("FakeHost not started")

    fun start(): Int = server.start()

    fun stop() {
        server.stop()
        _session.value?.close()
    }

    fun openPairing(): PairingPayload {
        val token = pairingWindow.open(clock())
        return PairingPayload("Fake Host", listOf("127.0.0.1"), port, identity.fingerprint, token.value, token.expiresAtMs)
    }

    suspend fun <T> push(type: MessageType, serializer: KSerializer<T>, body: T): Envelope =
        (_session.value ?: error("No client connected")).send(type, serializer, body)

    private suspend fun handle(session: BridgeSession) {
        val handshake = HostHandshake(
            hostName = "Fake Host",
            hostFingerprint = identity.fingerprint,
            appVersion = "test",
            registry = registry,
            pairingWindow = pairingWindow,
            approver = { request ->
                lastSas = request.sas
                approvePairing
            },
            clock = clock,
        )
        if (handshake.run(session) !is HostHandshakeResult.Authenticated) return
        _session.value?.close()
        _session.value = session
        scope.launch {
            session.incoming.collect { envelope ->
                received.send(envelope)
                onCommand(session, envelope)
            }
        }
    }
}
