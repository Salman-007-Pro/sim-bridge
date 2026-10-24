package com.simbridge.core.model

object PhoneNumbers {
    private val allowedChars = Regex("[^0-9+*#]")

    /** Strips formatting so the same contact maps to one thread key. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val leadingPlus = trimmed.startsWith("+")
        val digits = trimmed.replace(allowedChars, "").removePrefix("+")
        return if (leadingPlus) "+$digits" else digits
    }

    fun isDialable(raw: String): Boolean {
        val n = normalize(raw)
        return n.length in 3..20 && n.trimStart('+').isNotEmpty()
    }
}

/**
 * Rejects emergency and premium-rate numbers by default (PRD section 8).
 * The Host additionally consults the platform emergency-number database.
 */
class DialPolicy(
    private val emergencyNumbers: Set<String> = DEFAULT_EMERGENCY,
    private val premiumPrefixes: List<String> = DEFAULT_PREMIUM_PREFIXES,
) {
    enum class Verdict { ALLOWED, BLOCKED_EMERGENCY, BLOCKED_PREMIUM, INVALID }

    fun evaluate(raw: String): Verdict {
        val n = PhoneNumbers.normalize(raw)
        if (n in emergencyNumbers) return Verdict.BLOCKED_EMERGENCY
        if (!PhoneNumbers.isDialable(raw)) return Verdict.INVALID
        if (premiumPrefixes.any { n.startsWith(it) }) return Verdict.BLOCKED_PREMIUM
        return Verdict.ALLOWED
    }

    companion object {
        val DEFAULT_EMERGENCY = setOf(
            "15", "16", "115", "1122", "1124", "1915", "130",
            "911", "112", "999", "000", "08", "110", "118", "119",
        )
        val DEFAULT_PREMIUM_PREFIXES = listOf("0900", "+92900", "0092900")
    }
}
