package com.simbridge.client.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import com.simbridge.client.BuildConfig
import com.simbridge.client.data.ClientPrefs
import com.simbridge.client.data.Endpoint
import com.simbridge.client.di.ApplicationScope
import com.simbridge.core.crypto.AndroidKeystoreIdentityStore
import com.simbridge.core.crypto.PairingPayload
import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.model.Clock
import com.simbridge.core.model.SetupState
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.AuthFailure
import com.simbridge.core.protocol.Capabilities
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.HostStatus
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.Unpair
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.storage.pairing.PairedDevice
import com.simbridge.core.storage.pairing.PairedDeviceStore
import com.simbridge.core.transport.Backoff
import com.simbridge.core.transport.BridgeConnector
import com.simbridge.core.transport.BridgeServer
import com.simbridge.core.transport.BridgeSession
import com.simbridge.core.transport.CloseReason
import com.simbridge.core.transport.SessionClosedException
import com.simbridge.core.transport.discovery.NsdHostFinder
import com.simbridge.core.transport.handshake.ClientAuthResult
import com.simbridge.core.transport.handshake.ClientHandshake
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer

data class CommandStats(val sent: Int = 0, val acked: Int = 0, val rejected: Int = 0, val timedOut: Int = 0)

data class ClientState(
    val setup: SetupState = SetupState.NOT_PAIRED,
    val hostName: String? = null,
    val hostStatus: HostStatus? = null,
    val capabilities: Capabilities? = null,
    val endpoint: Endpoint? = null,
    val pairingCode: String? = null,
    val message: String? = null,
    val paused: Boolean = false,
    val stats: CommandStats = CommandStats(),
)

/**
 * The Client's connection to its paired Host. Holds no control authority of its own:
 * it forwards commands and projects the Host's state for the UI.
 */
@Singleton
class ClientBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val identityStore: AndroidKeystoreIdentityStore,
    private val pairedStore: PairedDeviceStore,
    private val prefs: ClientPrefs,
    private val clock: Clock,
) : HostLink {
    private sealed interface ConnectOutcome {
        data class Connected(val session: BridgeSession, val endpoint: Endpoint, val hostName: String) : ConnectOutcome
        data object Revoked : ConnectOutcome
        data class Unreachable(val reason: String) : ConnectOutcome
    }

    private val _state = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = _state.asStateFlow()

    private val handlers = CopyOnWriteArrayList<ClientMessageHandler>()
    private val finder = NsdHostFinder(context)
    private val backoff = Backoff()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loopJob: Job? = null
    private var pairingJob: Job? = null
    private var networkCallbackRegistered = false

    @Volatile private var session: BridgeSession? = null
    @Volatile private var revokedByHost = false

    private val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    fun register(handler: ClientMessageHandler) {
        if (handlers.none { it === handler }) handlers += handler
    }

    fun initialize() {
        registerNetworkCallback()
        scope.launch {
            val host = pairedStore.current().firstOrNull()
            if (host == null) {
                _state.update { it.copy(setup = SetupState.NOT_PAIRED) }
            } else {
                _state.update { it.copy(setup = SetupState.HOST_OFFLINE, hostName = host.name) }
                ensureLoop(null)
            }
        }
    }

    fun reconnectNow() {
        if (_state.value.paused) resume() else wake.trySend(Unit)
    }

    fun pause() {
        scope.launch {
            _state.update { it.copy(paused = true) }
            loopJob?.cancelAndJoin()
            session?.close(CloseReason.LOCAL)
            _state.update { it.copy(setup = SetupState.HOST_OFFLINE, message = "Paused") }
        }
    }

    fun resume() {
        _state.update { it.copy(paused = false, message = null) }
        ensureLoop(null)
    }

    fun pair(payload: PairingPayload) {
        pairingJob?.cancel()
        pairingJob = scope.launch(Dispatchers.IO) { runPairing(payload) }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        session?.close(CloseReason.LOCAL)
        _state.value = ClientState(setup = SetupState.NOT_PAIRED)
    }

    /** User-initiated unpair: tell the Host, forget it, rotate this phone's key and wipe local data. */
    suspend fun unpair() {
        loopJob?.cancelAndJoin()
        session?.let { current ->
            runCatching { current.request(MessageType.UNPAIR, Unpair.serializer(), Unpair("Removed on Client"), 3_000) }
            current.close(CloseReason.REVOKED)
        }
        forgetHost()
    }

    /** After the Host revoked this phone: clear the stale pairing so the user can pair again. */
    suspend fun forgetHost() {
        loopJob?.cancelAndJoin()
        session?.close(CloseReason.LOCAL)
        pairedStore.clear()
        prefs.clearAll()
        identityStore.rotate()
        handlers.forEach { runCatching { it.onWipe() } }
        revokedByHost = false
        _state.value = ClientState(setup = SetupState.NOT_PAIRED)
        BridgeLog.i(TAG, "Host forgotten, identity rotated")
    }

    suspend fun wipeLocalData() {
        handlers.forEach { runCatching { it.onWipe() } }
        prefs.clearSyncState()
        if (session != null) handlers.forEach { handler -> scope.launch { handler.onConnected(this@ClientBridge) } }
    }

    suspend fun setManualEndpoint(text: String?): Boolean {
        if (!text.isNullOrBlank() && ClientPrefs.parseEndpoint(text) == null) return false
        prefs.setManualEndpoint(text)
        reconnectNow()
        return true
    }

    override suspend fun <T> request(type: MessageType, serializer: KSerializer<T>, body: T, timeoutMs: Long): RequestOutcome {
        val current = session ?: return RequestOutcome.NotConnected
        bumpStats { it.copy(sent = it.sent + 1) }
        val reply = try {
            current.request(type, serializer, body, timeoutMs)
        } catch (e: SessionClosedException) {
            return RequestOutcome.NotConnected
        }
        return when {
            reply == null -> RequestOutcome.Timeout.also { bumpStats { s -> s.copy(timedOut = s.timedOut + 1) } }
            reply.type == MessageType.ERROR -> RequestOutcome.Rejected(reply.decodeBody(ErrorBody.serializer()))
                .also { bumpStats { s -> s.copy(rejected = s.rejected + 1) } }
            else -> RequestOutcome.Acked(reply).also { bumpStats { s -> s.copy(acked = s.acked + 1) } }
        }
    }

    override suspend fun <T> send(type: MessageType, serializer: KSerializer<T>, body: T): Boolean {
        val current = session ?: return false
        return runCatching { current.send(type, serializer, body) }.isSuccess
    }

    private fun bumpStats(transform: (CommandStats) -> CommandStats) = _state.update { it.copy(stats = transform(it.stats)) }

    @Synchronized
    private fun ensureLoop(initial: ConnectOutcome.Connected?) {
        if (loopJob?.isActive == true && initial == null) return
        loopJob = scope.launch(Dispatchers.IO) { connectionLoop(initial) }
    }

    private suspend fun connectionLoop(initial: ConnectOutcome.Connected?) {
        var pending: ConnectOutcome? = initial
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val host = pairedStore.current().firstOrNull()
            if (host == null) {
                _state.update { it.copy(setup = SetupState.NOT_PAIRED) }
                return
            }
            when (val outcome = pending ?: connect(host)) {
                is ConnectOutcome.Connected -> {
                    attempt = 0
                    runSession(outcome, host)
                    if (revokedByHost) return onRevoked()
                }
                ConnectOutcome.Revoked -> return onRevoked()
                is ConnectOutcome.Unreachable -> _state.update {
                    it.copy(setup = SetupState.HOST_OFFLINE, hostName = host.name, message = outcome.reason)
                }
            }
            pending = null
            withTimeoutOrNull(backoff.delayFor(attempt++)) { wake.receive() }
        }
    }

    private suspend fun connect(host: PairedDevice): ConnectOutcome {
        val identity = identityStore.getOrCreate()
        val tls = TlsContexts.create(identity, PinnedTrustManager { setOf(host.fingerprint) })
        val handshake = ClientHandshake(deviceName, BuildConfig.VERSION_NAME, identity.fingerprint)
        val direct = buildList {
            prefs.manualEndpoint()?.let(::add)
            host.lastAddress?.let { add(Endpoint(it, host.lastPort ?: BridgeServer.DEFAULT_PORT)) }
        }.distinct()
        for (endpoint in direct) tryEndpoint(tls, handshake, endpoint)?.let { return it }
        val discovered = discover(host.fingerprint)
        if (discovered != null && discovered !in direct) tryEndpoint(tls, handshake, discovered)?.let { return it }
        return ConnectOutcome.Unreachable("Host phone not found on this network")
    }

    private suspend fun tryEndpoint(tls: SSLContext, handshake: ClientHandshake, endpoint: Endpoint): ConnectOutcome? {
        val candidate = runCatching { BridgeConnector.connect(tls, endpoint.address, endpoint.port, scope) }.getOrNull()
            ?: return null
        return when (val result = handshake.hello(candidate)) {
            is ClientAuthResult.Authenticated -> ConnectOutcome.Connected(candidate, endpoint, result.hostName)
            is ClientAuthResult.PairingRequired -> {
                candidate.close(CloseReason.LOCAL)
                ConnectOutcome.Revoked
            }
            is ClientAuthResult.Failed -> if (result.failure == AuthFailure.NOT_PAIRED) ConnectOutcome.Revoked else null
        }
    }

    private suspend fun discover(fingerprint: String): Endpoint? = runCatching {
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            finder.discover().first { found -> found.fingerprintPrefix == null || fingerprint.startsWith(found.fingerprintPrefix!!) }
        }?.let { Endpoint(it.address, it.port) }
    }.getOrNull()

    private suspend fun runSession(connected: ConnectOutcome.Connected, host: PairedDevice) {
        val current = connected.session
        session = current
        pairedStore.touch(host.fingerprint, clock.now(), connected.endpoint.address, connected.endpoint.port)
        _state.update {
            it.copy(
                setup = SetupState.CONNECTED_CONTROL_ONLY,
                hostName = connected.hostName,
                endpoint = connected.endpoint,
                pairingCode = null,
                message = null,
            )
        }
        BridgeLog.i(TAG, "Connected to Host")
        coroutineScope {
            launch { handlers.forEach { handler -> runCatching { handler.onConnected(this@ClientBridge) } } }
            current.incoming.collect { envelope -> dispatch(current, envelope) }
        }
        session = null
        handlers.forEach { runCatching { it.onDisconnected() } }
        if (!revokedByHost) {
            _state.update { it.copy(setup = SetupState.HOST_OFFLINE, message = "Connection to Host lost") }
        }
        BridgeLog.i(TAG, "Disconnected: ${current.closeReason.value}")
    }

    private suspend fun dispatch(current: BridgeSession, envelope: Envelope) {
        when (envelope.type) {
            MessageType.CAPABILITIES -> {
                val caps = envelope.decodeBody(Capabilities.serializer())
                _state.update { it.copy(capabilities = caps, setup = connectedSetup(caps, it.hostStatus)) }
            }
            MessageType.HOST_STATUS -> {
                val status = envelope.decodeBody(HostStatus.serializer())
                _state.update { it.copy(hostStatus = status, setup = connectedSetup(it.capabilities, status)) }
            }
            MessageType.UNPAIR -> {
                revokedByHost = true
                runCatching { current.send(MessageType.ACK, Ack.serializer(), Ack(), correlationId = envelope.messageId) }
                current.close(CloseReason.REVOKED)
            }
            MessageType.ERROR -> BridgeLog.w(TAG, "Host error: ${envelope.decodeBody(ErrorBody.serializer()).code}")
            else -> handlers.firstOrNull { envelope.type in it.types }?.let { handler ->
                runCatching { handler.handle(envelope) }.onFailure { BridgeLog.e(TAG, "Handler failed for ${envelope.type}", it) }
            }
        }
    }

    private fun connectedSetup(caps: Capabilities?, status: HostStatus?): SetupState {
        val featuresMissing = caps != null && (caps.calls != CapabilityStatus.SUPPORTED || caps.sms != CapabilityStatus.SUPPORTED)
        val simMissing = status != null && !status.simReady
        return when {
            caps?.audio == AudioStatus.VERIFIED && !featuresMissing && !simMissing -> SetupState.CONNECTED_AUDIO_READY
            featuresMissing || simMissing -> SetupState.DEGRADED
            else -> SetupState.CONNECTED_CONTROL_ONLY
        }
    }

    private fun onRevoked() {
        revokedByHost = false
        _state.update {
            it.copy(setup = SetupState.REVOKED, message = "This phone was unpaired on the Host phone.", hostStatus = null)
        }
        BridgeLog.w(TAG, "Pairing revoked by Host")
    }

    private suspend fun runPairing(payload: PairingPayload) {
        loopJob?.cancelAndJoin()
        session?.close(CloseReason.LOCAL)
        _state.value = ClientState(setup = SetupState.PAIRING, hostName = payload.hostName)

        val identity = identityStore.getOrCreate()
        val tls = TlsContexts.create(identity, PinnedTrustManager { setOf(payload.hostFingerprint) })
        val handshake = ClientHandshake(deviceName, BuildConfig.VERSION_NAME, identity.fingerprint)

        var connected: BridgeSession? = null
        var endpoint: Endpoint? = null
        for (address in payload.addresses) {
            connected = runCatching { BridgeConnector.connect(tls, address, payload.port, scope) }.getOrNull()
            if (connected != null) {
                endpoint = Endpoint(address, payload.port)
                break
            }
        }
        if (connected == null || endpoint == null) {
            return pairingFailed("Couldn't reach ${payload.hostName}. Make sure both phones are on the same Wi-Fi or hotspot.")
        }

        val hostName = when (val hello = handshake.hello(connected)) {
            is ClientAuthResult.Authenticated -> hello.hostName
            is ClientAuthResult.PairingRequired -> {
                val result = handshake.pair(connected, payload.token, onSas = { code ->
                    _state.update { it.copy(setup = SetupState.PAIRING_CONFIRMATION, pairingCode = code) }
                })
                when (result) {
                    is ClientAuthResult.Authenticated -> result.hostName
                    is ClientAuthResult.Failed -> return pairingFailed(failureMessage(result.failure))
                    is ClientAuthResult.PairingRequired -> return pairingFailed("Pairing did not complete.")
                }
            }
            is ClientAuthResult.Failed -> return pairingFailed(failureMessage(hello.failure))
        }

        pairedStore.clear()
        pairedStore.upsert(
            PairedDevice(payload.hostFingerprint, hostName, clock.now(), clock.now(), endpoint.address, endpoint.port),
        )
        BridgeLog.i(TAG, "Paired with Host")
        ensureLoop(ConnectOutcome.Connected(connected, endpoint, hostName))
    }

    private fun pairingFailed(message: String) {
        _state.value = ClientState(setup = SetupState.NOT_PAIRED, message = message)
    }

    private fun failureMessage(failure: AuthFailure?): String = when (failure) {
        AuthFailure.PAIRING_EXPIRED -> "The pairing code expired. Show a new code on the Host phone."
        AuthFailure.PAIRING_REJECTED -> "Pairing was rejected on the Host phone."
        AuthFailure.NOT_PAIRED -> "The Host phone is not accepting new phones. Open pairing on the Host first."
        AuthFailure.UNSUPPORTED_VERSION -> "Update both apps to the same version."
        else -> "Pairing did not complete. Try again."
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val wifi = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(wifi, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wake.trySend(Unit)
            }
        })
        networkCallbackRegistered = true
    }

    private companion object {
        const val TAG = "ClientBridge"
        const val DISCOVERY_TIMEOUT_MS = 3_000L
    }
}
