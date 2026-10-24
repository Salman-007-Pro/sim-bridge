package com.simbridge.core.storage.ledger

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCommandLedgerTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        LedgerDatabase::class.java,
    ).build()
    private val ledger = RoomCommandLedger(db.ledgerDao())

    @After
    fun close() = db.close()

    @Test
    fun claimIsIdempotentAcrossCalls() = runTest {
        assertEquals(LedgerClaim.New, ledger.claim("k", "DIAL", 1))
        assertEquals(LedgerClaim.Duplicate(null), ledger.claim("k", "DIAL", 2))
        ledger.complete("k", "ok")
        assertEquals(LedgerClaim.Duplicate("ok"), ledger.claim("k", "DIAL", 3))
    }
}
