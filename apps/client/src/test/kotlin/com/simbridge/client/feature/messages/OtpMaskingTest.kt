package com.simbridge.client.feature.messages

import com.simbridge.client.data.ClientPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpMaskingTest {
    @Test
    fun masksCodesOnlyInOtpLikeMessages() {
        assertEquals("Your OTP is ••••••. Do not share.", OtpMasking.mask("Your OTP is 482913. Do not share."))
        assertEquals("Verification code: ••••", OtpMasking.mask("Verification code: 7731"))
        assertEquals("Meet at 1930 near gate 4", OtpMasking.mask("Meet at 1930 near gate 4"))
        assertFalse(OtpMasking.looksLikeOtp("Call me at 03001234567"))
        assertTrue(OtpMasking.looksLikeOtp("Bank PIN 1234"))
    }

    @Test
    fun manualEndpointParsingAcceptsOnlyIpv4() {
        assertEquals("192.168.43.1", ClientPrefs.parseEndpoint("192.168.43.1")?.address)
        assertEquals(50000, ClientPrefs.parseEndpoint(" 10.0.0.2:50000 ")?.port)
        assertNull(ClientPrefs.parseEndpoint("host.local:47820"))
        assertNull(ClientPrefs.parseEndpoint("10.0.0.2:80"))
        assertNull(ClientPrefs.parseEndpoint("10.0.0.2:1:2"))
    }
}
