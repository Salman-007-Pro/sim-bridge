package com.simbridge.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCryptoTest {
    private val hostFp = "a".repeat(64)
    private val clientFp = "b".repeat(64)

    @Test
    fun tokenIsSingleUse() {
        val window = PairingWindow()
        val token = window.open(nowMs = 0)
        assertTrue(PairingWindow.TOKEN_PATTERN.matches(token.value))
        assertTrue(window.isOpen(1))
        assertEquals(PairingWindow.ConsumeResult.ACCEPTED, window.consume(token.value, 1))
        assertEquals(PairingWindow.ConsumeResult.CLOSED, window.consume(token.value, 2))
        assertFalse(window.isOpen(2))
    }

    @Test
    fun wrongTokenBurnsTheWindow() {
        val window = PairingWindow()
        val token = window.open(0)
        assertEquals(PairingWindow.ConsumeResult.INVALID, window.consume("x".repeat(22), 1))
        assertEquals(PairingWindow.ConsumeResult.CLOSED, window.consume(token.value, 2))
    }

    @Test
    fun expiredTokenIsRejected() {
        val window = PairingWindow(ttlMs = 1_000)
        val token = window.open(0)
        assertFalse(window.isOpen(1_000))
        assertEquals(PairingWindow.ConsumeResult.EXPIRED, window.consume(token.value, 1_000))
    }

    @Test
    fun reopeningInvalidatesPreviousToken() {
        val window = PairingWindow()
        val first = window.open(0)
        val second = window.open(1)
        assertNotEquals(first.value, second.value)
        assertEquals(PairingWindow.ConsumeResult.INVALID, window.consume(first.value, 2))
    }

    @Test
    fun sasIsDeterministicSixDigitsAndBindsBothFingerprints() {
        val token = PairingWindow().open(0).value
        val code = Sas.compute(token, hostFp, clientFp)
        assertTrue(Regex("^[0-9]{6}$").matches(code))
        assertEquals(code, Sas.compute(token, hostFp.uppercase(), clientFp))
        assertNotEquals(code, Sas.compute(token, hostFp, "c".repeat(64)))
        assertNotEquals(code, Sas.compute(token, clientFp, hostFp))
    }

    @Test
    fun pairingPayloadRoundTrips() {
        val payload = PairingPayload("Salman's Pixel & Co", listOf("192.168.1.20", "10.0.0.5"), 47820, hostFp, "A".repeat(22), 123)
        assertEquals(payload, PairingPayload.parse(payload.toUri()).getOrThrow())
    }

    @Test
    fun maliciousPairingPayloadsAreRejected() {
        val good = PairingPayload("Host", listOf("192.168.1.20"), 47820, hostFp, "A".repeat(22), 123).toUri()
        val bad = listOf(
            "https://evil.example/pair",
            good.replace("192.168.1.20", "evil.example"),
            good.replace("192.168.1.20", "192.168.001.20"),
            good.replace("192.168.1.20", "1.2.3.4,1.2.3.5,1.2.3.6,1.2.3.7,1.2.3.8"),
            good.replace("p=47820", "p=80"),
            good.replace("p=47820", "p=99999"),
            good.replace("fp=$hostFp", "fp=xyz"),
            good.replace("t=${"A".repeat(22)}", "t=short"),
            good.replace("v=1", "v=2"),
            "$good&cmd=rm",
            good + "&n=" + "x".repeat(2_000),
        )
        for (input in bad) assertTrue("should reject: $input", PairingPayload.parse(input).isFailure)
    }

    @Test
    fun hostNameIsSanitized() {
        val uri = PairingPayload("Host", listOf("192.168.1.2"), 47820, hostFp, "A".repeat(22), 1).toUri()
            .replace("n=Host", "n=" + "%0A%07Evil" + "y".repeat(80))
        val parsed = PairingPayload.parse(uri).getOrThrow()
        assertFalse(parsed.hostName.any { it.isISOControl() })
        assertEquals(64, parsed.hostName.length)
    }
}
