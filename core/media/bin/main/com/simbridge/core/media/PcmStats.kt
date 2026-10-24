package com.simbridge.core.media

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Signal summary used by the lab to tell real captured speech from the zeros a blocked source returns. */
data class PcmStats(val samples: Int, val rms: Double, val peak: Int, val activeRatio: Double) {
    /** Heuristic only: a capture is "live" if a meaningful share of 20 ms windows rise above the noise floor. */
    val looksLive: Boolean get() = samples > 0 && rms > SILENCE_RMS && activeRatio > 0.05

    companion object {
        const val SILENCE_RMS = 30.0
        private const val ACTIVE_WINDOW_RMS = 120.0

        fun of(pcm: ShortArray, sampleRateHz: Int, length: Int = pcm.size): PcmStats {
            if (length <= 0) return PcmStats(0, 0.0, 0, 0.0)
            var sumSquares = 0.0
            var peak = 0
            for (i in 0 until length) {
                val v = pcm[i].toInt()
                sumSquares += v.toDouble() * v
                peak = maxOf(peak, abs(v))
            }
            val window = (sampleRateHz / 50).coerceAtLeast(1)
            var windows = 0
            var active = 0
            var start = 0
            while (start < length) {
                val end = minOf(start + window, length)
                var s = 0.0
                for (i in start until end) s += pcm[i].toDouble() * pcm[i]
                if (sqrt(s / (end - start)) > ACTIVE_WINDOW_RMS) active++
                windows++
                start = end
            }
            return PcmStats(length, sqrt(sumSquares / length), peak, active.toDouble() / windows)
        }

        /** Test tone for uplink-injection experiments: the remote party reports whether they hear it. */
        fun tone(frequencyHz: Double, sampleRateHz: Int, durationMs: Int, amplitude: Short = 8_000): ShortArray =
            ShortArray(sampleRateHz * durationMs / 1000) { i ->
                (amplitude * sin(2.0 * Math.PI * frequencyHz * i / sampleRateHz)).toInt().toShort()
            }
    }
}
