package com.simbridge.host.session

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.simbridge.core.crypto.AndroidKeystoreIdentityStore
import com.simbridge.core.crypto.PairingPayload
import com.simbridge.core.crypto.PairingWindow
import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.crypto.TlsIdentity
import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.Clock
import com.simbridge.core.model.Redaction
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.Capabilities
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.HostStatus
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.Unpair
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.storage.pairing.PairedDevice
import com.simbridge.core.storage.pairing.PairedDeviceStore
import com.simbridge.core.transport.BridgeServer
import com.simbridge.core.transport.BridgeSession
import com.simbridge.core.transport.CloseReason
import com.simbridge.core.transport.discovery.NsdAdvertiser
import com.simbridge.core.transport.handshake.HostHandshake
import com.simbridge.core.transport.handshake.HostHandshakeResult
import com.simbridge.core.transport.handshake.PairedClientRegistry
import com.simbridge.core.transport.handshake.PairingApprovalRequest
import com.simbridge.host.BuildConfig
import com.simbridge.host.di.ApplicationScope
import com.simbridge.host.feature.probe.CapabilityProbe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

data class ActiveClient(val name: String, val fingerprint: String, val address: String?, val connectedAtMs: Long)

data class HostBridgeState(
    val running: Boolean = false,
    val port: Int? = null,
    val addresses: List<String> = emptyList(),
    val activeClient: ActiveClient? = null,
    val error: String? = null,
)

sealed interface PairingState {
    data object Idle : PairingState
    data class Showing(val payload: PairingPayload) : PairingState
    data class AwaitingApproval(val request: PairingApprovalRequest) : PairingState
    data class Completed(val clientName: String) : PairingState
    data class Failed(val message: String) : PairingState
}

/**
 * The Host's single control authority: runs the TLS listener, pairing, the active Client session
 * and routes Client requests to feature handlers. Telephony state stays authoritative on this phone.
 */
@Singleton
class HostBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val identityStore: AndroidKeystoreIdentityStore,
    private val pairedStore: PairedDeviceStore,
    private val statusSource: HostStatusSource,
    private val probe: CapabilityProbe,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow(HostBridgeState())
    val state: StateFlow<HostBridgeState> = _state.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    val pairedClients = pairedStore.devices

    private val lifecycle = Mutex()
    private val handlers = CopyOnWriteArrayList<HostCommandHandler>()
    private val pairingWindow = PairingWindow()
    private val nsd = NsdAdvertiser(context)
    private var server: BridgeServer? = null
    private var statusJob: Job? = null
    private var pairingExpiryJob: Job? = null
    private var approval: CompletableDeferred<Boolean>? = null

    @Volatile private var identity: TlsIdentity? = null
    @Volatile private var activeSession: BridgeSession? = null

    val hostName: String
        get() = Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    fun register(handler: HostCommandHandler) {
        if (handlers.none { it === handler }) handlers += handler
    }

    fun start() {
        scope.launch(Dispatchers.IO) {
            lifecycle.withLock {
                if (server != null) return@withLock
                runCatching {
                    val id = identityStore.getOrCreate().also { identity = it }
                    val tls = TlsContexts.create(id, PinnedTrustManager(allowAnyPeer = true))
                    val newServer = BridgeServer(sslContext = { tls }, scope = scope) { session -> onSession(session) }
                    val port = newServer.start()
                    server = newServer
                    nsd.register("SIM Bridge ${hostName.take(24)}", port, id.fingerprint)
                    _state.update { it.copy(running = true, port = port, addresses = NetworkAddresses.localIpv4(), error = null) }
                    statusJob = scope.launch { statusSource.status.drop(1).collect { publish(MessageType.HOST_STATUS, HostStatus.serializer(), it) } }
                    BridgeLog.i(TAG, "Bridge listening on port $port")
                }.onFailure { error ->
                    BridgeLog.e(TAG, "Bridge failed to start", error)
                    _state.update { it.copy(running = false, error = "Bridge could not start: ${error.message}") }
                }
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            lifecycle.withLock {
                statusJob?.cancel()
                nsd.unregister()
                server?.stop()
                server = null
                activeSession?.close(CloseReason.LOCAL)
                activeSession = null
                cancelPairing()
                _state.update { HostBridgeState() }
            }
        }
    }

    fun refreshAddresses() = _state.update { it.copy(addresses = NetworkAddresses.localIpv4()) }

    fun startPairing(): PairingPayload? {
        val id = identity ?: return null
        val port = _state.value.port ?: return null
        val addresses = NetworkAddresses.localIpv4()
        _state.update { it.copy(addresses = addresses) }
        if (addresses.isEmpty()) {
            _pairing.value = PairingState.Failed("No Wi-Fi or hotspot network found on this phone.")
            return null
        }
        val token = pairingWindow.open(clock.now())
        val payload = PairingPayload(hostName, addresses, port, id.fingerprint, token.value, token.expiresAtMs)
        _pairing.value = PairingState.Showing(payload)
        pairingExpiryJob?.cancel()
        pairingExpiryJob = scope.launch {
            delay((token.expiresAtMs - clock.now()).coerceAtLeast(0))
            if (_pairing.value is PairingState.Showing) {
                pairingWindow.close()
                _pairing.value = PairingState.Failed("The pairing code expired. Create a new one.")
            }
        }
        return payload
    }

    fun cancelPairing() {
        pairingExpiryJob?.cancel()
        pairingWindow.close()
        approval?.complete(false)
        _pairing.value = PairingState.Idle
    }

    fun respondToPairing(approved: Boolean) {
        approval?.complete(approved)
    }

    suspend fun unpair(fingerprint: String) {
        val session = activeSession
        if (session != null && session.peerFingerprint == fingerprint) {
            runCatching { session.send(MessageType.UNPAIR, Unpair.serializer(), Unpair("Removed on Host")) }
            session.close(CloseReason.REVOKED)
        }
        pairedStore.remove(fingerprint)
        BridgeLog.i(TAG, "Unpaired client ${Redaction.fingerprint(fingerprint)}")
    }

    suspend fun unpairAll() {
        pairedStore.current().forEach { unpair(it.fingerprint) }
        handlers.forEach { it.onWipe() }
    }

    fun capabilities(): Capabilities {
        val report = probe.run()
        return Capabilities(calls = report.callsCapability, sms = report.smsCapability, audio = AudioStatus.UNSUPPORTED)
    }

    /** Re-sends capabilities after the user granted or removed a role. */
    fun refreshCapabilities() {
        scope.launch { publish(MessageType.CAPABILITIES, Capabilities.serializer(), capabilities()) }
    }

    /** Sends an event to the connected Client. Returns false when no Client is connected. */
    suspend fun <T> publish(type: MessageType, serializer: KSerializer<T>, body: T): Boolean {
        val session = activeSession ?: return false
        return runCatching { session.send(type, serializer, body) }.isSuccess
    }

    private suspend fun onSession(session: BridgeSession) {
        val id = identity ?: return session.close(CloseReason.LOCAL)
        val handshake = HostHandshake(
            hostName = hostName,
            hostFingerprint = id.fingerprint,
            appVersion = BuildConfig.VERSION_NAME,
            registry = registry,
            pairingWindow = pairingWindow,
            approver = { request -> awaitApproval(request) },
            clock = clock::now,
        )
        when (val result = handshake.run(session)) {
            is HostHandshakeResult.Rejected -> {
                BridgeLog.w(TAG, "Rejected connection: ${result.failure} ${result.detail}")
                if (_pairing.value is PairingState.AwaitingApproval || result.failure?.name?.startsWith("PAIRING") == true) {
                    _pairing.value = PairingState.Failed(result.detail)
                }
            }
            is HostHandshakeResult.Authenticated -> onAuthenticated(session, result)
        }
    }

    private suspend fun onAuthenticated(session: BridgeSession, result: HostHandshakeResult.Authenticated) {
        if (result.newlyPaired) {
            pairingExpiryJob?.cancel()
            _pairing.value = PairingState.Completed(result.clientName)
        }
        activeSession?.close(CloseReason.REPLACED)
        activeSession = session
        pairedStore.touch(session.peerFingerprint, clock.now(), session.remoteAddress)
        _state.update {
            it.copy(activeClient = ActiveClient(result.clientName, session.peerFingerprint, session.remoteAddress, clock.now()))
        }
        BridgeLog.i(TAG, "Client connected ${Redaction.fingerprint(session.peerFingerprint)}")

        session.send(MessageType.CAPABILITIES, Capabilities.serializer(), capabilities())
        session.send(MessageType.HOST_STATUS, HostStatus.serializer(), statusSource.read())
        handlers.forEach { runCatching { it.onClientConnected(session) } }

        scope.launch {
            session.incoming.collect { envelope -> route(session, envelope) }
        }
        scope.launch {
            val reason = session.awaitClosed()
            if (activeSession === session) {
                activeSession = null
                _state.update { it.copy(activeClient = null) }
            }
            BridgeLog.i(TAG, "Client session closed: $reason")
        }
    }

    private suspend fun route(session: BridgeSession, envelope: Envelope) {
        if (envelope.type == MessageType.UNPAIR) {
            runCatching { session.send(MessageType.ACK, Ack.serializer(), Ack(), correlationId = envelope.messageId) }
            pairedStore.remove(session.peerFingerprint)
            session.close(CloseReason.REVOKED)
            return
        }
        if (envelope.type == MessageType.ERROR || envelope.type == MessageType.ACK) return
        val handler = handlers.firstOrNull { envelope.type in it.types }
        if (handler == null) {
            session.send(
                MessageType.ERROR,
                ErrorBody.serializer(),
                ErrorBody(ErrorCode.NOT_SUPPORTED, "${envelope.type} is not supported by this Host"),
                correlationId = envelope.messageId,
            )
            return
        }
        runCatching { handler.handle(session, envelope) }.onFailure { error ->
            BridgeLog.e(TAG, "Handler failed for ${envelope.type}", error)
            runCatching {
                session.send(
                    MessageType.ERROR,
                    ErrorBody.serializer(),
                    ErrorBody(ErrorCode.INTERNAL, "Host could not complete ${envelope.type}"),
                    correlationId = envelope.messageId,
                )
            }
        }
    }

    private suspend fun awaitApproval(request: PairingApprovalRequest): Boolean {
        val decision = CompletableDeferred<Boolean>()
        approval = decision
        _pairing.value = PairingState.AwaitingApproval(request)
        return try {
            decision.await()
        } finally {
            approval = null
        }
    }

    private val registry = object : PairedClientRegistry {
        override suspend fun isPaired(fingerprint: String) = pairedStore.find(fingerprint) != null
        override suspend fun register(fingerprint: String, deviceName: String) {
            pairedStore.upsert(PairedDevice(fingerprint, deviceName, pairedAtMs = clock.now(), lastSeenAtMs = clock.now()))
        }
    }

    private companion object {
        const val TAG = "HostBridge"
    }
}
