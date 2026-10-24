package com.simbridge.core.transport

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Exponential reconnect delays capped low enough to meet the < 15 s reconnect target. */
class Backoff(
    private val initialMs: Long = 500,
    private val maxMs: Long = 8_000,
    private val jitter: Double = 0.2,
    private val random: Random = Random.Default,
) {
    fun delayFor(attempt: Int): Long {
        val base = min(maxMs.toDouble(), initialMs * 2.0.pow(attempt.coerceIn(0, 16)))
        val spread = base * jitter
        return (base - spread + random.nextDouble() * 2 * spread).toLong().coerceAtLeast(0)
    }
}
