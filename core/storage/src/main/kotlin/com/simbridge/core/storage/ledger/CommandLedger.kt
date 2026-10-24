package com.simbridge.core.storage.ledger

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LedgerClaim {
    data object New : LedgerClaim

    /** [outcome] is null while the first attempt is in flight or if its result was never recorded. */
    data class Duplicate(val outcome: String?) : LedgerClaim
}

/**
 * Idempotency ledger. A command is claimed *before* it executes, so a retry after a crash or
 * reconnect is reported as a duplicate instead of running twice (for example, never redial).
 */
interface CommandLedger {
    suspend fun claim(key: String, commandType: String, nowMs: Long): LedgerClaim
    suspend fun complete(key: String, outcome: String)
    suspend fun prune(olderThanMs: Long)
    suspend fun clear()
}

class InMemoryCommandLedger : CommandLedger {
    private data class Entry(val type: String, val createdAtMs: Long, var outcome: String?)

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    override suspend fun claim(key: String, commandType: String, nowMs: Long): LedgerClaim = mutex.withLock {
        val existing = entries[key]
        if (existing != null) return LedgerClaim.Duplicate(existing.outcome)
        entries[key] = Entry(commandType, nowMs, null)
        LedgerClaim.New
    }

    override suspend fun complete(key: String, outcome: String) = mutex.withLock {
        entries[key]?.outcome = outcome
    }

    override suspend fun prune(olderThanMs: Long) = mutex.withLock {
        entries.entries.removeAll { it.value.createdAtMs < olderThanMs }
        Unit
    }

    override suspend fun clear() = mutex.withLock { entries.clear() }
}
