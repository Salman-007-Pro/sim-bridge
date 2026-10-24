package com.simbridge.core.media

/**
 * Wire format for one encrypted audio frame sent over UDP:
 * `version(1) | seq(4 BE) | timestampMs(4 BE) | sealed payload`.
 * The GCM nonce is derived from the sequence, so it is not transmitted.
 */
data class MediaPacket(val seq: Int, val timestampMs: Int, val sealed: ByteArray) {
    fun encode(): ByteArray {
        val out = ByteArray(HEADER_BYTES + sealed.size)
        out[0] = VERSION
        putInt(out, 1, seq)
        putInt(out, 5, timestampMs)
        sealed.copyInto(out, HEADER_BYTES)
        return out
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MediaPacket && seq == other.seq && timestampMs == other.timestampMs && sealed.contentEquals(other.sealed))

    override fun hashCode(): Int = (seq * 31 + timestampMs) * 31 + sealed.contentHashCode()

    companion object {
        const val VERSION: Byte = 1
        const val HEADER_BYTES = 9

        fun decode(bytes: ByteArray, length: Int = bytes.size): MediaPacket? {
            if (length < HEADER_BYTES || bytes[0] != VERSION) return null
            val sealed = bytes.copyOfRange(HEADER_BYTES, length)
            return MediaPacket(getInt(bytes, 1), getInt(bytes, 5), sealed)
        }

        private fun putInt(dst: ByteArray, at: Int, value: Int) {
            dst[at] = (value ushr 24).toByte()
            dst[at + 1] = (value ushr 16).toByte()
            dst[at + 2] = (value ushr 8).toByte()
            dst[at + 3] = value.toByte()
        }

        private fun getInt(src: ByteArray, at: Int): Int =
            (src[at].toInt() and 0xff shl 24) or
                (src[at + 1].toInt() and 0xff shl 16) or
                (src[at + 2].toInt() and 0xff shl 8) or
                (src[at + 3].toInt() and 0xff)
    }
}
