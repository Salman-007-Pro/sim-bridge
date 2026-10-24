package com.simbridge.core.crypto

import java.security.MessageDigest
import java.security.cert.X509Certificate

object Fingerprints {
    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    /** Lowercase hex SHA-256 of the DER-encoded certificate. */
    fun of(certificate: X509Certificate): String = sha256Hex(certificate.encoded)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun isValid(fingerprint: String): Boolean = HEX_64.matches(fingerprint)

    fun matches(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.lowercase().toByteArray(), b.lowercase().toByteArray())
}
