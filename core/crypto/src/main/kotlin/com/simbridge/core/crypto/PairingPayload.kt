package com.simbridge.core.crypto

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Contents of the Host's pairing QR code. Parsing is defensive: a QR is untrusted input,
 * so only IPv4 literals (no DNS lookups), bounded lengths and known fields are accepted.
 */
data class PairingPayload(
    val hostName: String,
    val addresses: List<String>,
    val port: Int,
    val hostFingerprint: String,
    val token: String,
    val expiresAtMs: Long,
) {
    fun toUri(): String = buildString {
        append("$SCHEME://pair?v=$VERSION")
        append("&n=").append(encode(hostName))
        append("&a=").append(addresses.joinToString(","))
        append("&p=").append(port)
        append("&fp=").append(hostFingerprint)
        append("&t=").append(token)
        append("&e=").append(expiresAtMs)
    }

    companion object {
        const val SCHEME = "simbridge"
        const val VERSION = 1
        private const val MAX_LENGTH = 1024
        private const val MAX_ADDRESSES = 4
        private const val MAX_NAME = 64
        private val ALLOWED_KEYS = setOf("v", "n", "a", "p", "fp", "t", "e")

        fun parse(text: String): Result<PairingPayload> = runCatching {
            require(text.length <= MAX_LENGTH) { "QR code is too long" }
            val prefix = "$SCHEME://pair?"
            require(text.startsWith(prefix)) { "Not a SIM Bridge pairing code" }
            val params = text.removePrefix(prefix).split("&").associate { part ->
                val idx = part.indexOf('=')
                require(idx > 0) { "Malformed parameter" }
                val key = part.substring(0, idx)
                require(key in ALLOWED_KEYS) { "Unknown parameter" }
                key to URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8.name())
            }
            require(params["v"] == VERSION.toString()) { "Unsupported pairing code version" }
            val name = params["n"].orEmpty().filter { !it.isISOControl() }.take(MAX_NAME).ifBlank { "Host phone" }
            val addresses = params["a"].orEmpty().split(",").filter { it.isNotBlank() }
            require(addresses.isNotEmpty() && addresses.size <= MAX_ADDRESSES) { "Invalid address list" }
            require(addresses.all(::isIpv4Literal)) { "Invalid address" }
            val port = params["p"]?.toIntOrNull()
            require(port != null && port in 1024..65535) { "Invalid port" }
            val fp = params["fp"].orEmpty()
            require(Fingerprints.isValid(fp)) { "Invalid fingerprint" }
            val token = params["t"].orEmpty()
            require(PairingWindow.TOKEN_PATTERN.matches(token)) { "Invalid token" }
            val expires = params["e"]?.toLongOrNull()
            require(expires != null && expires > 0) { "Invalid expiry" }
            PairingPayload(name, addresses, port, fp, token, expires)
        }

        fun isIpv4Literal(value: String): Boolean {
            val parts = value.split(".")
            if (parts.size != 4) return false
            return parts.all { p -> p.length in 1..3 && p.all(Char::isDigit) && p.toInt() in 0..255 && (p == "0" || !p.startsWith("0")) }
        }

        private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
