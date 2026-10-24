package com.simbridge.core.media

import com.simbridge.core.model.AudioStatus

/**
 * Master switch for the voice media path. It stays off until a provider passes the PRD 6.4
 * physical-call gate for the exact Host build. The media engine below is fully built and tested,
 * but [isAvailable] returns false in production, so no call ever carries audio without evidence.
 */
object AudioFeature {
    /** Compile-time flag. Flip to true only in a branch that also ships a verified provider. */
    const val ENABLED = false

    fun isAvailable(status: AudioStatus): Boolean = ENABLED && status == AudioStatus.VERIFIED
}
