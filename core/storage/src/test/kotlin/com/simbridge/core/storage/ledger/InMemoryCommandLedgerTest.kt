package com.simbridge.core.storage.ledger

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryCommandLedgerTest {
    @Test
    fun secondClaimIsDuplicateWithRecordedOutcome() = runTest {
        val ledger = InMemoryCommandLedger()
        assertEquals(LedgerClaim.New, ledger.claim("k1", "DIAL", 10))
        assertEquals(LedgerClaim.Duplicate(null), ledger.claim("k1", "DIAL", 11))
        ledger.complete("k1", "callId=c9")
        assertEquals(LedgerClaim.Duplicate("callId=c9"), ledger.claim("k1", "DIAL", 12))
    }

    @Test
    fun pruneForgetsOldEntries() = runTest {
        val ledger = InMemoryCommandLedger()
        ledger.claim("old", "SMS", 1)
        ledger.claim("new", "SMS", 100)
        ledger.prune(olderThanMs = 50)
        assertEquals(LedgerClaim.New, ledger.claim("old", "SMS", 101))
        assertEquals(LedgerClaim.Duplicate(null), ledger.claim("new", "SMS", 102))
    }
}
