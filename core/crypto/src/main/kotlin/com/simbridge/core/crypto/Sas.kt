package com.simbridge.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Short authentication string: both phones derive the same 6 digits from the one-time token and
 * both certificate fingerprints. A man-in-the-middle with a different certificate yields different digits.
 */
object Sas {
    fun compute(token: String, hostFingerprint: String, clientFingerprint: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(), "HmacSHA256"))
        val digest = mac.doFinal("simbridge-sas-v1|${hostFingerprint.lowercase()}|${clientFingerprint.lowercase()}".toByteArray())
        val value = ((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)
        return (value % 1_000_000).toString().padStart(6, '0')
    }
}
