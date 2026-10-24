package com.simbridge.core.media

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class MediaDirection(val salt: ByteArray) {
    HOST_TO_CLIENT(byteArrayOf(0x68, 0x32, 0x63, 0x01)),
    CLIENT_TO_HOST(byteArrayOf(0x63, 0x32, 0x68, 0x01)),
}

/**
 * Per-call media protection. The 32-byte master secret is created by the Host and sent to the
 * Client over the already mutually-authenticated TLS control channel, then both sides derive two
 * independent AES-256-GCM keys (one per direction) with HKDF-SHA256.
 *
 * Nonce = 4-byte direction salt || 4 zero bytes || 4-byte packet sequence. The key is random per
 * call and a 32-bit sequence never wraps within a call, so a (key, nonce) pair is never reused.
 */
class MediaCrypto private constructor(
    private val sendKey: SecretKeySpec,
    private val sendSalt: ByteArray,
    private val recvKey: SecretKeySpec,
    private val recvSalt: ByteArray,
) {
    fun seal(seq: Int, plaintext: ByteArray, offset: Int = 0, length: Int = plaintext.size): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(TAG_BITS, nonce(sendSalt, seq)))
        return cipher.doFinal(plaintext, offset, length)
    }

    /** Returns null if the packet fails authentication (tampered, wrong key or wrong sequence). */
    fun open(seq: Int, ciphertext: ByteArray, offset: Int = 0, length: Int = ciphertext.size): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, recvKey, GCMParameterSpec(TAG_BITS, nonce(recvSalt, seq)))
        cipher.doFinal(ciphertext, offset, length)
    } catch (e: GeneralSecurityException) {
        null
    }

    private fun nonce(salt: ByteArray, seq: Int): ByteArray = ByteArray(NONCE_BYTES).also { n ->
        salt.copyInto(n, 0)
        n[8] = (seq ushr 24).toByte()
        n[9] = (seq ushr 16).toByte()
        n[10] = (seq ushr 8).toByte()
        n[11] = seq.toByte()
    }

    companion object {
        const val MASTER_KEY_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val NONCE_BYTES = 12
        private const val KEY_BYTES = 32

        fun forHost(masterKey: ByteArray): MediaCrypto = create(masterKey, MediaDirection.HOST_TO_CLIENT)
        fun forClient(masterKey: ByteArray): MediaCrypto = create(masterKey, MediaDirection.CLIENT_TO_HOST)

        private fun create(masterKey: ByteArray, send: MediaDirection): MediaCrypto {
            require(masterKey.size == MASTER_KEY_BYTES) { "Master key must be $MASTER_KEY_BYTES bytes" }
            val recv = if (send == MediaDirection.HOST_TO_CLIENT) MediaDirection.CLIENT_TO_HOST else MediaDirection.HOST_TO_CLIENT
            val prk = hkdfExtract(masterKey)
            return MediaCrypto(
                sendKey = SecretKeySpec(hkdfExpand(prk, "simbridge-media ${send.name} key", KEY_BYTES), "AES"),
                sendSalt = send.salt,
                recvKey = SecretKeySpec(hkdfExpand(prk, "simbridge-media ${recv.name} key", KEY_BYTES), "AES"),
                recvSalt = recv.salt,
            )
        }

        private fun hkdfExtract(ikm: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
            doFinal(ikm)
        }

        private fun hkdfExpand(prk: ByteArray, info: String, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var generated = 0
            var counter = 1
            while (generated < length) {
                mac.update(previous)
                mac.update(info.toByteArray())
                mac.update(counter.toByte())
                previous = mac.doFinal()
                val take = minOf(previous.size, length - generated)
                previous.copyInto(out, generated, 0, take)
                generated += take
                counter++
            }
            return out
        }
    }
}
