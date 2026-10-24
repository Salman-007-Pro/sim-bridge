package com.simbridge.core.transport

import com.simbridge.core.crypto.Fingerprints
import com.simbridge.core.crypto.TlsContexts
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Host-side TLS listener. Every accepted connection must complete a mutual TLS 1.3 handshake
 * before a [BridgeSession] is created; authorization happens afterwards in the handshake layer.
 * [acceptFilter] runs before any TLS work, and at most [maxPendingHandshakes] handshakes run at once.
 */
class BridgeServer(
    private val sslContext: () -> SSLContext,
    private val scope: CoroutineScope,
    private val preferredPort: Int = DEFAULT_PORT,
    private val sessionConfig: SessionConfig = SessionConfig(),
    private val handshakeTimeoutMs: Int = 10_000,
    private val maxPendingHandshakes: Int = 4,
    private val acceptFilter: (Socket) -> Boolean = { true },
    private val onSession: suspend (BridgeSession) -> Unit,
) {
    private var serverSocket: SSLServerSocket? = null
    private var acceptJob: Job? = null
    private val pendingHandshakes = AtomicInteger(0)

    val port: Int? get() = serverSocket?.localPort

    @Synchronized
    fun start(): Int {
        serverSocket?.let { return it.localPort }
        val socket = bind(sslContext())
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun bind(context: SSLContext): SSLServerSocket {
        fun open(port: Int) = (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            reuseAddress = true
            bind(InetSocketAddress(port), BACKLOG)
        }
        val socket = try {
            open(preferredPort)
        } catch (e: IOException) {
            open(0)
        }
        socket.needClientAuth = true
        socket.enabledProtocols = arrayOf(TlsContexts.PROTOCOL)
        return socket
    }

    private suspend fun acceptLoop(server: SSLServerSocket) {
        while (scope.isActive && !server.isClosed) {
            val socket = try {
                server.accept() as SSLSocket
            } catch (e: IOException) {
                if (server.isClosed) return else continue
            }
            if (!runCatching { acceptFilter(socket) }.getOrDefault(false)) {
                runCatching { socket.close() }
                continue
            }
            if (pendingHandshakes.incrementAndGet() > maxPendingHandshakes) {
                pendingHandshakes.decrementAndGet()
                runCatching { socket.close() }
                continue
            }
            scope.launch(Dispatchers.IO) { handle(socket) }
        }
    }

    private suspend fun handle(socket: SSLSocket) {
        val session = try {
            socket.tcpNoDelay = true
            socket.soTimeout = handshakeTimeoutMs
            socket.startHandshake()
            socket.soTimeout = 0
            val peer = socket.session.peerCertificates.first() as X509Certificate
            BridgeSession(socket, Fingerprints.of(peer), null, scope, sessionConfig)
        } catch (e: Exception) {
            runCatching { socket.close() }
            return
        } finally {
            pendingHandshakes.decrementAndGet()
        }
        session.start()
        onSession(session)
    }

    companion object {
        const val DEFAULT_PORT = 47820
        private const val BACKLOG = 8

        /** Accept only connections that arrived on a private LAN / hotspot address, never a public or cellular one. */
        val localNetworkOnly: (Socket) -> Boolean = { socket ->
            val local = socket.localAddress
            local.isSiteLocalAddress || local.isLinkLocalAddress || local.isLoopbackAddress
        }
    }
}

/** Client-side connector: TCP connect, TLS 1.3 handshake against the pinned Host certificate. */
object BridgeConnector {
    suspend fun connect(
        sslContext: SSLContext,
        address: String,
        port: Int,
        scope: CoroutineScope,
        sessionConfig: SessionConfig = SessionConfig(),
        connectTimeoutMs: Int = 4_000,
        handshakeTimeoutMs: Int = 8_000,
    ): BridgeSession = withContext(Dispatchers.IO) {
        val raw = Socket()
        try {
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(address, port), connectTimeoutMs)
            val ssl = sslContext.socketFactory.createSocket(raw, address, port, true) as SSLSocket
            ssl.enabledProtocols = arrayOf(TlsContexts.PROTOCOL)
            ssl.soTimeout = handshakeTimeoutMs
            ssl.startHandshake()
            ssl.soTimeout = 0
            val peer = ssl.session.peerCertificates.first() as X509Certificate
            BridgeSession(ssl, Fingerprints.of(peer), UUID.randomUUID().toString(), scope, sessionConfig).also { it.start() }
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }
}
