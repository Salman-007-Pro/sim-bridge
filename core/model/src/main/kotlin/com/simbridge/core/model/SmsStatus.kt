package com.simbridge.core.model

import kotlinx.serialization.Serializable

/**
 * Outbound: QUEUED -> SENT_TO_HOST -> SUBMITTED_TO_MODEM -> SENT -> DELIVERED, or FAILED.
 * Network submission ([SENT]) is distinct from recipient delivery ([DELIVERED]).
 * Inbound messages are always [RECEIVED].
 */
@Serializable
enum class SmsStatus(private val rank: Int) {
    QUEUED(0),
    SENT_TO_HOST(1),
    SUBMITTED_TO_MODEM(2),
    SENT(3),
    DELIVERED(4),
    FAILED(5),
    RECEIVED(6);

    val isFinal: Boolean get() = this == DELIVERED || this == FAILED || this == RECEIVED

    /** Late or duplicate receipts must never move a message backwards. */
    fun canAdvanceTo(next: SmsStatus): Boolean = when {
        this == RECEIVED || next == RECEIVED -> false
        isFinal -> false
        next == FAILED -> true
        else -> next.rank > rank
    }
}

@Serializable
enum class SmsDirection { INBOUND, OUTBOUND }
