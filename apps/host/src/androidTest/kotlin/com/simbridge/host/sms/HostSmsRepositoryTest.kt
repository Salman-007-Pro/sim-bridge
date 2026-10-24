package com.simbridge.host.sms

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.core.model.Clock
import com.simbridge.core.model.SmsStatus
import com.simbridge.host.data.HostDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostSmsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        HostDatabase::class.java,
    ).build()
    private val repository = HostSmsRepository(db, Clock { 1_000 })

    @After
    fun close() = db.close()

    @Test
    fun sameClientMessageIdIsClaimedOnlyOnce() = runTest {
        assertNotNull(repository.claimOutbound("m1", "0300 1234567", "hi", parts = 1))
        assertNull(repository.claimOutbound("m1", "0300 1234567", "hi", parts = 1))
        assertEquals("03001234567", repository.findByClientId("m1")?.address)
    }

    @Test
    fun multipartBecomesSentOnlyAfterAllPartsAndNeverRegresses() = runTest {
        val sms = repository.claimOutbound("m2", "0300", "long", parts = 2)!!
        repository.update(sms.id) { it.copy(status = SmsStatus.SUBMITTED_TO_MODEM) }
        assertEquals(SmsStatus.SUBMITTED_TO_MODEM, repository.onPartSent(sms.id, ok = true, errorCode = null)?.status)
        assertEquals(SmsStatus.SENT, repository.onPartSent(sms.id, ok = true, errorCode = null)?.status)
        repository.onPartDelivered(sms.id, ok = true, errorCode = null)
        assertEquals(SmsStatus.DELIVERED, repository.onPartDelivered(sms.id, ok = true, errorCode = null)?.status)
        val late = repository.update(sms.id) { it.copy(status = SmsStatus.SENT) }
        assertNull("a late SENT must not overwrite DELIVERED", late)
    }

    @Test
    fun everyChangeAdvancesTheSyncCheckpoint() = runTest {
        val a = repository.insertInbound("0300", "one", null)
        val b = repository.claimOutbound("m3", "0300", "two", 1)!!
        val c = repository.update(b.id) { it.copy(status = SmsStatus.SUBMITTED_TO_MODEM) }!!
        assertTrue(a.changeSeq < b.changeSeq && b.changeSeq < c.changeSeq)
        assertEquals(listOf(b.id), repository.changesSince(b.changeSeq - 1, 10).map { it.id }.distinct())
        assertEquals(1, repository.changesSince(a.changeSeq, 10).size)
    }
}
