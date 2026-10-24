package com.simbridge.core.transport

import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsSendRequest
import com.simbridge.core.testing.FakeHost
import com.simbridge.core.testing.TestIdentities
import com.simbridge.core.transport.handshake.ClientAuthResult
import com.simbridge.core.transport.handshake.ClientHandshake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD section 12: >= 99% command ACK on a stable link, reconnect < 15 s.
 * Run longer with: ./gradlew :core:transport:testDebugUnitTest -PsoakSeconds=600
 */
class ReliabilityTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientIdentity = TestIdentities.create("client")
    private val host = FakeHost(scope)
    private val handshake = ClientHandshake("Client", "test", clientIdentity.fingerprint)

    @After
    fun tearDown() {
        host.stop()
        scope.cancel()
    }

    private suspend fun connect(port: Int): BridgeSession = BridgeConnector.connect(
        TlsContexts.create(clientIdentity, PinnedTrustManager { setOf(host.identity.fingerprint) }),
        "127.0.0.1", port, scope,
    )

    private suspend fun pairedSession(): BridgeSession {
        val payload = host.openPairing()
        val session = connect(host.port)
        handshake.hello(session)
        check(handshake.pair(session, payload.token, onSas = {}) is ClientAuthResult.Authenticated)
        return session
    }

    @Test
    fun commandAcknowledgementRateMeetsTarget() = runBlocking {
        host.start()
        val session = pairedSession()
        val seconds = System.getProperty("simbridge.soakSeconds")?.toLongOrNull() ?: 3
        val deadline = System.currentTimeMillis() + seconds * 1000
        var sent = 0
        var acked = 0
        while (System.currentTimeMillis() < deadline) {
            sent++
            val reply = session.request(
                MessageType.SMS_SEND_REQUEST,
                SmsSendRequest.serializer(),
                SmsSendRequest("m$sent", "03001234567", "soak $sent"),
                timeoutMs = 2_000,
            )
            if (reply?.type == MessageType.ACK) acked++
        }
        val ratio = acked.toDouble() / sent
        println("Reliability: $acked / $sent acknowledged (${"%.3f".format(ratio * 100)}%) over ${seconds}s")
        assertTrue("too few commands to judge: $sent", sent >= 100)
        assertTrue("ACK ratio $ratio below 99%", ratio >= 0.99)
    }

    @Test
    fun reconnectsQuicklyAfterHostRestart() = runBlocking {
        val port = host.start()
        pairedSession().close()
        host.stop()
        delay(200)
        val restartedAt = System.currentTimeMillis()
        host.start()
        val backoff = Backoff()
        var attempt = 0
        var authenticated = false
        while (!authenticated && System.currentTimeMillis() - restartedAt < 15_000) {
            authenticated = runCatching { handshake.hello(connect(host.port)) is ClientAuthResult.Authenticated }.getOrDefault(false)
            if (!authenticated) delay(backoff.delayFor(attempt++))
        }
        val elapsed = System.currentTimeMillis() - restartedAt
        println("Reconnect after Host restart took ${elapsed}ms (previous port $port, new port ${host.port})")
        assertTrue("did not reconnect within 15 s", authenticated)
    }
}
