package com.simbridge.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PairingToken(val value: String, val expiresAtMs: Long) {
    fun isExpired(nowMs: Long) = nowMs >= expiresAtMs
}

/**
 * Holds at most one short-lived, single-use pairing token. Opening a new window
 * invalidates the previous token; a token is consumed by its first use, valid or not.
 */
class PairingWindow(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val random: SecureRandom = SecureRandom(),
) {
    enum class ConsumeResult { ACCEPTED, EXPIRED, INVALID, CLOSED }

    private var current: PairingToken? = null

    @Synchronized
    fun open(nowMs: Long): PairingToken {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = PairingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), nowMs + ttlMs)
        current = token
        return token
    }

    @Synchronized
    fun close() {
        current = null
    }

    @Synchronized
    fun isOpen(nowMs: Long): Boolean = current?.isExpired(nowMs) == false

    @Synchronized
    fun consume(candidate: String, nowMs: Long): ConsumeResult {
        val token = current ?: return ConsumeResult.CLOSED
        current = null
        if (token.isExpired(nowMs)) return ConsumeResult.EXPIRED
        val same = MessageDigest.isEqual(token.value.toByteArray(), candidate.toByteArray())
        return if (same) ConsumeResult.ACCEPTED else ConsumeResult.INVALID
    }

    companion object {
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
        const val TOKEN_BYTES = 16
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
    }
}
