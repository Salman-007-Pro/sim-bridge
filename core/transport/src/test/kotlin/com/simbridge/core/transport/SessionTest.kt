package com.simbridge.core.transport

import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.DecodeResult
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.ProtocolCodec
import com.simbridge.core.protocol.SmsSendRequest
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.testing.TestIdentities
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hostIdentity = TestIdentities.create("host")
    private val clientIdentity = TestIdentities.create("client")
    private val servers = mutableListOf<BridgeServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        scope.cancel()
    }

    private fun clientContext(pinnedHost: String = hostIdentity.fingerprint): SSLContext =
        TlsContexts.create(clientIdentity, PinnedTrustManager { setOf(pinnedHost) })

    private fun startServer(config: SessionConfig = SessionConfig()): Pair<Int, CompletableDeferred<BridgeSession>> {
        val accepted = CompletableDeferred<BridgeSession>()
        val server = BridgeServer(
            sslContext = { TlsContexts.create(hostIdentity, PinnedTrustManager(allowAnyPeer = true)) },
            scope = scope,
            preferredPort = 0,
            sessionConfig = config,
        ) { accepted.complete(it) }
        servers += server
        return server.start() to accepted
    }

    @Test
    fun frameIoRoundTripsAndRejectsBadLengths() {
        val out = ByteArrayOutputStream()
        FrameIO.write(DataOutputStream(out), "hello".toByteArray())
        assertEquals("hello", String(FrameIO.read(DataInputStream(ByteArrayInputStream(out.toByteArray())))))

        val oversize = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(FrameIO.MAX_FRAME_BYTES + 1) }
        assertThrows<FrameException> { FrameIO.read(DataInputStream(ByteArrayInputStream(oversize.toByteArray()))) }
        val negative = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(-4) }
        assertThrows<FrameException> { FrameIO.read(DataInputStream(ByteArrayInputStream(negative.toByteArray()))) }
        val truncated = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(10); write(byteArrayOf(1, 2)) } }
        assertThrows<EOFException> { FrameIO.read(DataInputStream(ByteArrayInputStream(truncated.toByteArray()))) }
    }

    @Test
    fun mutualTlsExchangesFingerprintsAndRoutesReplies() = runBlocking {
        withTimeout(15_000) {
            val (port, accepted) = startServer()
            val client = BridgeConnector.connect(clientContext(), "127.0.0.1", port, scope)
            val host = accepted.await()
            assertEquals(hostIdentity.fingerprint, client.peerFingerprint)
            assertEquals(clientIdentity.fingerprint, host.peerFingerprint)

            scope.launch {
                val request = host.incoming.first()
                assertEquals(client.sessionId, host.sessionId)
                host.send(MessageType.ACK, Ack.serializer(), Ack("queued"), correlationId = request.messageId)
            }
            val reply = client.request(MessageType.SMS_SEND_REQUEST, SmsSendRequest.serializer(), SmsSendRequest("m1", "0300", "hi"))
            assertNotNull(reply)
            assertEquals(MessageType.ACK, reply!!.type)
            assertEquals("queued", reply.decodeBody(Ack.serializer()).detail)
            client.close()
            val reason = withTimeout(5_000) { host.awaitClosed() }
            assertTrue("unexpected $reason", reason == CloseReason.PEER_CLOSED || reason == CloseReason.IO_ERROR)
        }
    }

    @Test
    fun clientRejectsHostThatIsNotPinned() = runBlocking {
        val (port, _) = startServer()
        try {
            BridgeConnector.connect(clientContext(pinnedHost = "0".repeat(64)), "127.0.0.1", port, scope)
            fail("Handshake should fail for an unpinned Host certificate")
        } catch (expected: SSLException) {
        }
    }

    @Test
    fun hostRejectsClientWithoutCertificate() = runBlocking {
        val (port, accepted) = startServer()
        val noIdentity = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(PinnedTrustManager { setOf(hostIdentity.fingerprint) }), null)
        }
        runCatching {
            val session = BridgeConnector.connect(noIdentity, "127.0.0.1", port, scope)
            withTimeout(5_000) { session.awaitClosed() }
        }
        kotlinx.coroutines.delay(500)
        assertTrue("Host must not create a session", !accepted.isCompleted)
    }

    @Test
    fun silentPeerTimesOut() = runBlocking {
        val server = (TlsContexts.create(hostIdentity, PinnedTrustManager(allowAnyPeer = true)).serverSocketFactory
            .createServerSocket(0) as SSLServerSocket).apply { needClientAuth = true }
        thread(isDaemon = true) { runCatching { (server.accept() as SSLSocket).also { it.startHandshake(); Thread.sleep(10_000) } } }
        val config = SessionConfig(heartbeatIntervalMs = 100, heartbeatTimeoutMs = 400)
        val client = BridgeConnector.connect(clientContext(), "127.0.0.1", server.localPort, scope, config)
        assertEquals(CloseReason.TIMEOUT, withTimeout(5_000) { client.awaitClosed() })
        server.close()
    }

    @Test
    fun replayedAndMalformedFramesGetErrorsWithoutKillingTheSession() = runBlocking {
        withTimeout(15_000) {
            val (port, accepted) = startServer()
            val raw = rawClient(port)
            val output = DataOutputStream(BufferedOutputStream(raw.outputStream))
            val input = DataInputStream(BufferedInputStream(raw.inputStream))
            val codec = ProtocolCodec()
            val ack = Envelope(1, "m-1", "s-raw", 1, 0, MessageType.ACK, body = ProtocolCodec.encodeBody(MessageType.ACK, Ack.serializer(), Ack()))

            FrameIO.write(output, codec.encode(ack))
            val host = accepted.await()
            assertEquals("m-1", host.incoming.first().messageId)

            FrameIO.write(output, codec.encode(ack.copy(messageId = "m-2")))
            assertError(codec, input, ErrorCode.REPLAY)

            FrameIO.write(output, "{not json".toByteArray())
            assertError(codec, input, ErrorCode.MALFORMED)

            FrameIO.write(output, codec.encode(ack.copy(messageId = "m-3", sessionId = "other", seq = 5)))
            assertError(codec, input, ErrorCode.SESSION_MISMATCH)

            FrameIO.write(output, codec.encode(ack.copy(messageId = "m-4", seq = 6)))
            assertEquals("m-4", host.incoming.first().messageId)
            assertTrue(host.isOpen)
            raw.close()
        }
    }

    @Test
    fun acceptFilterDropsConnectionsBeforeTls() = runBlocking {
        val accepted = CompletableDeferred<BridgeSession>()
        val server = BridgeServer(
            sslContext = { TlsContexts.create(hostIdentity, PinnedTrustManager(allowAnyPeer = true)) },
            scope = scope,
            preferredPort = 0,
            acceptFilter = { false },
        ) { accepted.complete(it) }
        servers += server
        val port = server.start()
        val result = runCatching { BridgeConnector.connect(clientContext(), "127.0.0.1", port, scope) }
        assertTrue("TLS must not complete when the filter rejects", result.isFailure)
        assertTrue(!accepted.isCompleted)
        assertTrue(BridgeServer.localNetworkOnly(Socket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }))
    }

    @Test
    fun excessSilentConnectionsAreRefused() = runBlocking {
        val server = BridgeServer(
            sslContext = { TlsContexts.create(hostIdentity, PinnedTrustManager(allowAnyPeer = true)) },
            scope = scope,
            preferredPort = 0,
            handshakeTimeoutMs = 5_000,
            maxPendingHandshakes = 2,
        ) { }
        servers += server
        val port = server.start()
        val silent = List(2) { Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 1_000) } }
        kotlinx.coroutines.delay(300)
        val extra = Socket().apply {
            connect(InetSocketAddress("127.0.0.1", port), 1_000)
            soTimeout = 2_000
        }
        assertEquals("server should close the extra connection at once", -1, extra.getInputStream().read())
        (silent + extra).forEach { it.close() }
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        val backoff = Backoff(initialMs = 500, maxMs = 8_000, jitter = 0.0)
        assertEquals(500, backoff.delayFor(0))
        assertEquals(1_000, backoff.delayFor(1))
        assertEquals(4_000, backoff.delayFor(3))
        assertEquals(8_000, backoff.delayFor(10))
    }

    private fun rawClient(port: Int): SSLSocket {
        val socket = Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 2_000) }
        return (clientContext().socketFactory.createSocket(socket, "127.0.0.1", port, true) as SSLSocket).apply {
            enabledProtocols = arrayOf(TlsContexts.PROTOCOL)
            startHandshake()
        }
    }

    private fun assertError(codec: ProtocolCodec, input: DataInputStream, expected: ErrorCode) {
        while (true) {
            val decoded = codec.decode(FrameIO.read(input)) as DecodeResult.Ok
            if (decoded.envelope.type == MessageType.HEARTBEAT) continue
            assertEquals(MessageType.ERROR, decoded.envelope.type)
            assertEquals(expected, decoded.envelope.decodeBody(ErrorBody.serializer()).code)
            return
        }
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }
}
