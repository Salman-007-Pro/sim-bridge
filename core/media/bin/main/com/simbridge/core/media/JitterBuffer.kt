package com.simbridge.core.media

import java.util.TreeMap

sealed interface PlayoutResult {
    /** Deliver this decoded-ready payload to the speaker / uplink. */
    data class Frame(val seq: Int, val payload: ByteArray) : PlayoutResult

    /** A packet was lost: play concealment (silence) and move on. */
    data object Gap : PlayoutResult

    /** Nothing ready yet: still filling, or the stream is idle. */
    data object Waiting : PlayoutResult
}

/**
 * Reorders UDP packets and smooths arrival jitter. It fills to [targetDepth] before playout starts,
 * then releases one frame per [poll]. Packets older than the last played frame are dropped as late;
 * a missing sequence becomes a [PlayoutResult.Gap] once later packets have arrived or the buffer is full.
 */
class JitterBuffer(
    private val targetDepth: Int = 3,
    private val capacity: Int = 50,
) {
    private val buffer = TreeMap<Int, ByteArray>()
    private var nextSeq = -1
    private var started = false
    var lateDropped = 0L
        private set
    var concealed = 0L
        private set

    val size: Int get() = buffer.size

    /** Returns false if the packet was dropped as late or duplicate. */
    fun offer(seq: Int, payload: ByteArray): Boolean {
        if (started && seq < nextSeq) {
            lateDropped++
            return false
        }
        if (buffer.containsKey(seq)) return false
        if (buffer.size >= capacity) buffer.pollFirstEntry()
        buffer[seq] = payload
        return true
    }

    fun poll(): PlayoutResult {
        if (!started) {
            if (buffer.size < targetDepth) return PlayoutResult.Waiting
            started = true
            nextSeq = buffer.firstKey()
        }
        if (buffer.isEmpty()) return PlayoutResult.Waiting
        buffer.remove(nextSeq)?.let { payload ->
            val seq = nextSeq
            nextSeq++
            return PlayoutResult.Frame(seq, payload)
        }
        // nextSeq is missing. Wait for it unless later packets have piled up (it is probably lost).
        if (buffer.size < targetDepth) return PlayoutResult.Waiting
        concealed++
        nextSeq++
        return PlayoutResult.Gap
    }

    fun reset() {
        buffer.clear()
        nextSeq = -1
        started = false
    }
}
