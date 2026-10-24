package com.simbridge.core.transport

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class FrameException(message: String) : IOException(message)

/** Length-prefixed frames: 4-byte big-endian size followed by the payload. */
object FrameIO {
    const val MAX_FRAME_BYTES = 512 * 1024

    fun write(output: DataOutputStream, payload: ByteArray) {
        if (payload.isEmpty() || payload.size > MAX_FRAME_BYTES) throw FrameException("Invalid frame size ${payload.size}")
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    fun read(input: DataInputStream): ByteArray {
        val length = input.readInt()
        if (length <= 0 || length > MAX_FRAME_BYTES) throw FrameException("Invalid frame length $length")
        return ByteArray(length).also(input::readFully)
    }
}
