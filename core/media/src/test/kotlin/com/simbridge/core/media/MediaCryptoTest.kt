package com.simbridge.core.media

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCryptoTest {
    private val master = ByteArray(MediaCrypto.MASTER_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    private val host = MediaCrypto.forHost(master)
    private val client = MediaCrypto.forClient(master)

    @Test
    fun eachDirectionDecryptsOnTheOtherSide() {
        val up = "client speaking".toByteArray()
        val down = "host downlink".toByteArray()
        assertArrayEquals(up, host.open(5, client.seal(5, up)))
        assertArrayEquals(down, client.open(9, host.seal(9, down)))
    }

    @Test
    fun wrongSequenceFailsAuthentication() {
        val sealed = host.seal(7, "frame".toByteArray())
        assertNull("nonce is bound to the sequence", client.open(8, sealed))
        assertArrayEquals("frame".toByteArray(), client.open(7, sealed))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = host.seal(1, "frame".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertNull(client.open(1, sealed))
    }

    @Test
    fun aDifferentMasterKeyCannotDecrypt() {
        val other = MediaCrypto.forClient(ByteArray(MediaCrypto.MASTER_KEY_BYTES) { 7 })
        assertNull(other.open(1, host.seal(1, "frame".toByteArray())))
    }

    @Test
    fun receiverNeverDecryptsItsOwnDirection() {
        // Host sealing h2c must not be openable by another host instance (which also decrypts c2h only).
        val peerHost = MediaCrypto.forHost(master)
        assertNull(peerHost.open(3, host.seal(3, "x".toByteArray())))
    }

    @Test
    fun sealedOutputIsNotPlaintext() {
        val plain = "secret audio".toByteArray()
        val sealed = host.seal(2, plain)
        assertTrue("GCM adds a 16-byte tag", sealed.size == plain.size + 16)
        assertFalse(sealed.copyOf(plain.size).contentEquals(plain))
    }
}
