package com.simbridge.client.feature.messages

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.client.data.ClientDatabase
import com.simbridge.client.feature.messages.data.MessagesRepository
import com.simbridge.client.session.HostLink
import com.simbridge.client.session.RequestOutcome
import com.simbridge.core.model.Clock
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsMessage
import com.simbridge.core.protocol.SmsStatusUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagesRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        ClientDatabase::class.java,
    ).build()

    private val offline = object : HostLink {
        override suspend fun <T> request(type: MessageType, serializer: KSerializer<T>, body: T, timeoutMs: Long) =
            RequestOutcome.NotConnected
        override suspend fun <T> send(type: MessageType, serializer: KSerializer<T>, body: T) = false
    }

    private fun repository(scope: TestScope) = MessagesRepository(db, Clock { 5_000 }, { offline }, scope)

    @After
    fun close() = db.close()

    @Test
    fun offlineSendStaysQueuedAndHostEchoIsMergedNotDuplicated() = runTest {
        val repo = repository(this)
        val thread = repo.send("0300 1234567", "hello")
        val queued = repo.thread(thread).first().single()
        assertEquals(SmsStatus.QUEUED, queued.status)

        val isNew = repo.applyRemote(
            SmsMessage(42, queued.localId, "03001234567", "hello", SmsDirection.OUTBOUND, SmsStatus.SUBMITTED_TO_MODEM, 6_000),
        )
        assertFalse(isNew)
        val merged = repo.thread(thread).first().single()
        assertEquals(42L, merged.hostSmsId)
        assertEquals(SmsStatus.SUBMITTED_TO_MODEM, merged.status)
    }

    @Test
    fun duplicateDeliveryReportsDoNotRegressStatus() = runTest {
        val repo = repository(this)
        repo.applyRemote(SmsMessage(7, null, "0300", "x", SmsDirection.OUTBOUND, SmsStatus.SENT, 1))
        repo.applyStatus(SmsStatusUpdate(7, null, SmsStatus.DELIVERED))
        repo.applyStatus(SmsStatusUpdate(7, null, SmsStatus.SENT))
        repo.applyStatus(SmsStatusUpdate(7, null, SmsStatus.DELIVERED))
        assertEquals(SmsStatus.DELIVERED, repo.thread("0300").first().single().status)
    }

    @Test
    fun inboundMessageIsNewOnceAndUnreadUntilOpened() = runTest {
        val repo = repository(this)
        val inbound = SmsMessage(9, null, "+923001234567", "salam", SmsDirection.INBOUND, SmsStatus.RECEIVED, 2)
        assertTrue(repo.applyRemote(inbound))
        assertFalse(repo.applyRemote(inbound))
        assertEquals(1, repo.unreadCount().first())
        repo.markRead("+923001234567")
        assertEquals(0, repo.unreadCount().first())
    }
}
