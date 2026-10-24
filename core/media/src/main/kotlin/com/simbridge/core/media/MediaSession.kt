package com.simbridge.core.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaStats(
    val framesSent: Long = 0,
    val framesPlayed: Long = 0,
    val concealed: Long = 0,
    val authFailures: Long = 0,
    val lateDropped: Long = 0,
)

/** Source of outbound audio: Host downlink capture, or Client microphone. Returns null to stop. */
fun interface AudioSource {
    suspend fun read(): ShortArray?
}

/** Sink for inbound audio: Client speaker, or Host uplink injection. */
fun interface AudioSink {
    suspend fun write(pcm: ShortArray)
}

/**
 * The bidirectional voice engine: capture -> encode -> encrypt -> packetise -> UDP, and
 * UDP -> depacketise -> decrypt -> jitter buffer -> decode -> playout. Codec, crypto, jitter and
 * transport are all injected, so the whole pipeline runs and is verified on the JVM over a pair of
 * in-memory transports before it is ever wired to real audio hardware.
 */
class MediaSession(
    private val codec: AudioCodec,
    private val crypto: MediaCrypto,
    private val transport: DatagramTransport,
    private val source: AudioSource,
    private val sink: AudioSink,
    private val scope: CoroutineScope,
    private val jitter: JitterBuffer = JitterBuffer(),
) {
    private val _stats = MutableStateFlow(MediaStats())
    val stats: StateFlow<MediaStats> = _stats.asStateFlow()

    private val jobs = mutableListOf<Job>()
    private val silence = ShortArray(codec.format.samplesPerFrame)
    private var running = false

    fun start() {
        if (running) return
        running = true
        jobs += scope.launch { sendLoop() }
        jobs += scope.launch { receiveLoop() }
        jobs += scope.launch { playoutLoop() }
    }

    fun stop() {
        running = false
        jobs.forEach(Job::cancel)
        jobs.clear()
        transport.close()
        codec.release()
    }

    private suspend fun sendLoop() {
        var seq = 0
        var timestampMs = 0
        while (running && currentCoroutineContext().isActive) {
            val pcm = source.read() ?: break
            val sealed = crypto.seal(seq, codec.encode(pcm))
            transport.send(MediaPacket(seq, timestampMs, sealed).encode())
            seq++
            timestampMs += codec.format.frameMs
            _stats.update { it.copy(framesSent = it.framesSent + 1) }
        }
    }

    private suspend fun receiveLoop() {
        while (running && currentCoroutineContext().isActive) {
            val bytes = transport.receive() ?: continue
            val packet = MediaPacket.decode(bytes) ?: continue
            val plain = crypto.open(packet.seq, packet.sealed)
            if (plain == null) {
                _stats.update { it.copy(authFailures = it.authFailures + 1) }
                continue
            }
            jitter.offer(packet.seq, plain)
        }
    }

    private suspend fun playoutLoop() {
        while (running && currentCoroutineContext().isActive) {
            when (val result = jitter.poll()) {
                is PlayoutResult.Frame -> {
                    sink.write(codec.decode(result.payload))
                    _stats.update { it.copy(framesPlayed = it.framesPlayed + 1, lateDropped = jitter.lateDropped) }
                }
                PlayoutResult.Gap -> {
                    sink.write(silence)
                    _stats.update { it.copy(concealed = jitter.concealed) }
                }
                PlayoutResult.Waiting -> Unit
            }
            delay(codec.format.frameMs.toLong())
        }
    }
}
