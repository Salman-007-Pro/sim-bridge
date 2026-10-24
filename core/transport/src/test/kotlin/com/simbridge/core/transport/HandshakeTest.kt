package com.simbridge.core.transport

import com.simbridge.core.crypto.PinnedTrustManager
import com.simbridge.core.crypto.TlsContexts
import com.simbridge.core.protocol.AuthFailure
import com.simbridge.core.testing.FakeHost
import com.simbridge.core.testing.TestIdentities
import com.simbridge.core.transport.handshake.ClientAuthResult
import com.simbridge.core.transport.handshake.ClientHandshake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HandshakeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientIdentity = TestIdentities.create("client")
    private lateinit var host: FakeHost
    private val handshake = ClientHandshake("Client phone", "test", clientIdentity.fingerprint)

    @Before
    fun setUp() {
        host = FakeHost(scope)
        host.start()
    }

    @After
    fun tearDown() {
        host.stop()
        scope.cancel()
    }

    private suspend fun connect(): BridgeSession = BridgeConnector.connect(
        TlsContexts.create(clientIdentity, PinnedTrustManager { setOf(host.identity.fingerprint) }),
        "127.0.0.1",
        host.port,
        scope,
    )

    @Test
    fun pairsWithMatchingSasThenReconnectsWithoutPairing() = runBlocking {
        withTimeout(20_000) {
            val payload = host.openPairing()
            val session = connect()
            val hello = handshake.hello(session)
            assertTrue(hello is ClientAuthResult.PairingRequired)

            var clientSas: String? = null
            val result = handshake.pair(session, payload.token, onSas = { clientSas = it })
            assertTrue(result is ClientAuthResult.Authenticated)
            assertEquals(host.lastSas, clientSas)
            assertTrue(host.registry.clients.containsKey(clientIdentity.fingerprint))
            session.close()

            val again = connect()
            assertTrue(handshake.hello(again) is ClientAuthResult.Authenticated)
            again.close()
        }
    }

    @Test
    fun unpairedClientWithoutPairingWindowIsRejected() = runBlocking {
        withTimeout(10_000) {
            val result = handshake.hello(connect())
            assertEquals(AuthFailure.NOT_PAIRED, (result as ClientAuthResult.Failed).failure)
        }
    }

    @Test
    fun wrongTokenIsRejected() = runBlocking {
        withTimeout(10_000) {
            host.openPairing()
            val session = connect()
            handshake.hello(session)
            val result = handshake.pair(session, "Z".repeat(22), onSas = {})
            assertEquals(AuthFailure.PAIRING_REJECTED, (result as ClientAuthResult.Failed).failure)
            assertTrue(host.registry.clients.isEmpty())
        }
    }

    @Test
    fun hostUserRejectionIsRespected() = runBlocking {
        withTimeout(10_000) {
            host.approvePairing = false
            val payload = host.openPairing()
            val session = connect()
            handshake.hello(session)
            val result = handshake.pair(session, payload.token, onSas = {})
            assertEquals(AuthFailure.PAIRING_REJECTED, (result as ClientAuthResult.Failed).failure)
            assertTrue(host.registry.clients.isEmpty())
        }
    }

    @Test
    fun expiredTokenIsRejected() = runBlocking {
        withTimeout(10_000) {
            var now = 0L
            host.clock = { now }
            val payload = host.openPairing()
            val session = connect()
            handshake.hello(session)
            now += 3 * 60 * 1000
            val result = handshake.pair(session, payload.token, onSas = {})
            assertEquals(AuthFailure.PAIRING_EXPIRED, (result as ClientAuthResult.Failed).failure)
        }
    }
}
