package com.simbridge.core.media

import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionTest {
    /** Lossless in-memory link between two endpoints, with an optional "drop every Nth packet". */
    private class Pipe(private val dropEvery: Int = 0) {
        val aToB = Channel<ByteArray>(Channel.UNLIMITED)
        val bToA = Channel<ByteArray>(Channel.UNLIMITED)
        private var sent = 0

        fun end(outbound: Channel<ByteArray>, inbound: Channel<ByteArray>) = object : DatagramTransport {
            override val localPort = 0
            override suspend fun send(bytes: ByteArray) {
                sent++
                if (dropEvery > 0 && sent % dropEvery == 0) return
                outbound.trySend(bytes)
            }
            override suspend fun receive(): ByteArray? = inbound.receive()
            override fun close() {}
        }
    }

    private fun master() = ByteArray(MediaCrypto.MASTER_KEY_BYTES).also { SecureRandom().nextBytes(it) }

    @Test
    fun hostDownlinkReachesClientSpeakerIntactAndEncrypted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val key = master()
        val pipe = Pipe()
        val frames = (0 until 25).map { ShortArray(AudioFormat.VOICE.samplesPerFrame) { i -> (it * 100 + i).toShort() } }
        val source = frames.iterator()
        val played = ConcurrentLinkedQueue<ShortArray>()
        val wireSeen = ConcurrentLinkedQueue<ByteArray>()

        val hostTransport = object : DatagramTransport {
            override val localPort = 0
            override suspend fun send(bytes: ByteArray) {
                wireSeen += bytes
                pipe.aToB.trySend(bytes)
            }
            override suspend fun receive(): ByteArray? = pipe.bToA.receive()
            override fun close() {}
        }
        val clientTransport = pipe.end(pipe.bToA, pipe.aToB)

        val host = MediaSession(
            codec = RawPcmCodec(), crypto = MediaCrypto.forHost(key), transport = hostTransport,
            source = { if (source.hasNext()) source.next() else null }, sink = {}, scope = this,
            jitter = JitterBuffer(targetDepth = 2),
        )
        val client = MediaSession(
            codec = RawPcmCodec(), crypto = MediaCrypto.forClient(key), transport = clientTransport,
            source = { null }, sink = { pcm -> played += pcm }, scope = this,
            jitter = JitterBuffer(targetDepth = 2),
        )
        client.start()
        host.start()
        advanceTimeBy(2_000)
        host.stop()
        client.stop()

        assertTrue("client should play most frames", played.size >= frames.size - 3)
        played.toList().take(frames.size - 3).forEachIndexed { index, pcm -> assertArrayEquals(frames[index], pcm) }
        wireSeen.forEach { wire ->
            val sealed = MediaPacket.decode(wire)!!.sealed
            assertTrue("payload on the wire must be encrypted (has GCM tag)", sealed.size > AudioFormat.VOICE.samplesPerFrame * 2)
        }
    }

    @Test
    fun lostPacketsAreConcealedNotFatal() = runTest {
        val key = master()
        val pipe = Pipe(dropEvery = 7)
        val frames = (0 until 40).map { ShortArray(AudioFormat.VOICE.samplesPerFrame) { 1 } }
        val source = frames.iterator()
        var played = 0
        val host = MediaSession(
            RawPcmCodec(), MediaCrypto.forHost(key), pipe.end(pipe.aToB, pipe.bToA),
            { if (source.hasNext()) source.next() else null }, {}, this, JitterBuffer(targetDepth = 3),
        )
        val client = MediaSession(
            RawPcmCodec(), MediaCrypto.forClient(key), pipe.end(pipe.bToA, pipe.aToB),
            { null }, { played++ }, this, JitterBuffer(targetDepth = 3),
        )
        client.start()
        host.start()
        advanceTimeBy(3_000)
        val concealed = client.stats.value.concealed
        host.stop()
        client.stop()
        assertTrue("playout continues through loss", played >= 30)
        assertTrue("dropped packets are concealed", concealed >= 1)
    }
}
