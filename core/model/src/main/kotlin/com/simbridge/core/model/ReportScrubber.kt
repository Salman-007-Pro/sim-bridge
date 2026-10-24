package com.simbridge.core.model

/**
 * Last line of defence before a debug report leaves the phone: masks anything that looks like a
 * phone number, pairing token, full certificate fingerprint or one-time code, even if a caller forgot.
 */
object ReportScrubber {
    private val fingerprint = Regex("\\b[0-9a-fA-F]{64}\\b")
    private val token = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{22}(?![A-Za-z0-9_-])")
    private val phone = Regex("(?<![0-9])\\+?[0-9][0-9 ()-]{6,}[0-9](?![0-9])")
    private val shortCode = Regex("(?<![0-9:.])[0-9]{4,6}(?![0-9:.])")

    fun scrub(text: String): String = text
        .replace(fingerprint) { it.value.take(8) + "…" }
        .replace(token) { if (it.value.any(Char::isDigit) && it.value.any(Char::isLetter)) "<token>" else it.value }
        .replace(phone) { Redaction.phone(it.value) }
        .replace(shortCode) { "#".repeat(it.value.length) }
}
