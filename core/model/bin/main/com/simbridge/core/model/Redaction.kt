package com.simbridge.core.model

/** Log-safe renderings of sensitive values. Never log raw numbers, bodies or tokens. */
object Redaction {
    fun phone(raw: String?): String {
        if (raw.isNullOrBlank()) return "<none>"
        val n = PhoneNumbers.normalize(raw)
        if (n.length <= 4) return "*".repeat(n.length)
        return n.take(3) + "*".repeat(n.length - 5) + n.takeLast(2)
    }

    fun body(raw: String?): String = if (raw == null) "<none>" else "<${raw.length} chars>"

    fun secret(@Suppress("UNUSED_PARAMETER") raw: String?): String = "<redacted>"

    fun fingerprint(hex: String?): String = if (hex == null) "<none>" else hex.take(8) + "…"
}
