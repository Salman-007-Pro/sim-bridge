package com.simbridge.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportScrubberTest {
    @Test
    fun masksSensitiveValuesButKeepsStructure() {
        val fp = "ab".repeat(32)
        val raw = """
            12:01:02.345 I/HostBridge: Client connected $fp
            SMS from +92 300 1234567 OTP 482913
            token=Ab3dEfGhIjKlMnOpQrStU9
            port 47820 api 36
        """.trimIndent()
        val out = ReportScrubber.scrub(raw)
        assertFalse(out.contains(fp))
        assertTrue(out.contains("abababab…"))
        assertFalse(out.contains("1234567"))
        assertFalse(out.contains("482913"))
        assertFalse(out.contains("Ab3dEfGhIjKlMnOpQrStU9"))
        assertTrue("timestamps survive", out.contains("12:01:02.345"))
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        assertEquals("Bridge listening, calls=SUPPORTED", ReportScrubber.scrub("Bridge listening, calls=SUPPORTED"))
    }
}
