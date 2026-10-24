package com.simbridge.client.feature.messages

/** Hides one-time codes in notification text so they cannot be read from a locked or shared screen. */
object OtpMasking {
    private val keywords = Regex("(?i)\\b(otp|code|pin|password|passcode|verification|verify|token)\\b")
    private val codes = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    fun looksLikeOtp(body: String): Boolean = keywords.containsMatchIn(body) && codes.containsMatchIn(body)

    fun mask(body: String): String = if (looksLikeOtp(body)) codes.replace(body) { "•".repeat(it.value.length) } else body
}
