package com.simbridge.core.protocol

/** Accepts only strictly increasing sequence numbers from one peer within one session. */
class SequenceGuard {
    private var last = 0L

    @Synchronized
    fun accept(seq: Long): Boolean {
        if (seq <= last) return false
        last = seq
        return true
    }
}
