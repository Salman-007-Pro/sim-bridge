package com.simbridge.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioFormat(val sampleRateHz: Int, val channels: Int, val frameMs: Int) {
    val samplesPerFrame: Int get() = sampleRateHz * frameMs / 1000 * channels

    companion object {
        /** Narrowband-friendly default: 16 kHz mono, 20 ms frames (320 samples). */
        val VOICE = AudioFormat(sampleRateHz = 16_000, channels = 1, frameMs = 20)
    }
}

/** Encodes one PCM frame to bytes and back. Kept separate from transport, crypto and network. */
interface AudioCodec {
    val name: String
    val format: AudioFormat
    fun encode(pcm: ShortArray): ByteArray
    fun decode(bytes: ByteArray): ShortArray
    fun release() = Unit
}

/**
 * Little-endian 16-bit PCM passthrough. Correct and fully testable on the JVM; it is the default
 * until an Opus codec is validated on real hardware (never claim a codec works before it is tested).
 */
class RawPcmCodec(override val format: AudioFormat = AudioFormat.VOICE) : AudioCodec {
    override val name = "pcm16"

    override fun encode(pcm: ShortArray): ByteArray {
        val out = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach(out::putShort)
        return out.array()
    }

    override fun decode(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }
}
