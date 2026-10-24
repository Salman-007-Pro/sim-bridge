package com.simbridge.client.feature.messages.data

import androidx.room.withTransaction
import com.simbridge.client.data.ClientDatabase
import com.simbridge.client.di.ApplicationScope
import com.simbridge.client.session.HostLink
import com.simbridge.client.session.RequestOutcome
import com.simbridge.core.model.Clock
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsMessage
import com.simbridge.core.protocol.SmsSendRequest
import com.simbridge.core.protocol.SmsStatusUpdate
import com.simbridge.core.protocol.decodeBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local projection of the Host's SMS. Outgoing messages are queued locally and sent with a stable
 * clientMessageId, so re-sending after a reconnect can never produce a second carrier SMS.
 */
@Singleton
class MessagesRepository @Inject constructor(
    private val db: ClientDatabase,
    private val clock: Clock,
    private val link: Provider<HostLink>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val dao = db.messages()
    private val outboxLock = Mutex()

    fun threads(): Flow<List<ThreadSummary>> = dao.threads()
    fun thread(threadKey: String): Flow<List<MessageEntity>> = dao.thread(threadKey)
    fun unreadCount(): Flow<Int> = dao.unreadCount()

    suspend fun markRead(threadKey: String) = dao.markThreadRead(threadKey)

    /** Stores the message as QUEUED and sends it in the background. Returns the thread key. */
    suspend fun send(to: String, text: String): String {
        val threadKey = PhoneNumbers.normalize(to)
        dao.upsert(
            MessageEntity(
                localId = UUID.randomUUID().toString(),
                hostSmsId = null,
                threadKey = threadKey,
                address = threadKey,
                body = text,
                direction = SmsDirection.OUTBOUND,
                status = SmsStatus.QUEUED,
                timestampMs = clock.now(),
            ),
        )
        scope.launch { flushOutbox(link.get()) }
        return threadKey
    }

    /** FAILED is final, so a retry is a new message with a new clientMessageId. */
    suspend fun retry(message: MessageEntity) {
        send(message.address, message.body)
    }

    suspend fun flushOutbox(link: HostLink) = outboxLock.withLock {
        for (message in dao.queued()) {
            val outcome = link.request(
                MessageType.SMS_SEND_REQUEST,
                SmsSendRequest.serializer(),
                SmsSendRequest(message.localId, message.address, message.body),
                timeoutMs = 15_000,
            )
            when (outcome) {
                is RequestOutcome.Acked -> {
                    val hostId = outcome.reply.takeIf { it.type == MessageType.ACK }
                        ?.decodeBody(Ack.serializer())?.detail?.toLongOrNull()
                    advance(message.localId, SmsStatus.SENT_TO_HOST, hostSmsId = hostId)
                }
                is RequestOutcome.Rejected -> advance(message.localId, SmsStatus.FAILED, errorText = outcome.error.message)
                RequestOutcome.Timeout, RequestOutcome.NotConnected -> return@withLock
            }
        }
    }

    /** Applies a message from the Host. Returns true if it is a new inbound message. */
    suspend fun applyRemote(message: SmsMessage): Boolean = db.withTransaction {
        val existing = message.clientMessageId?.let { dao.find(it) } ?: dao.findByHostId(message.hostSmsId)
        if (existing != null) {
            dao.upsert(existing.copy(hostSmsId = message.hostSmsId, status = merged(existing.status, message.status)))
            false
        } else {
            val threadKey = PhoneNumbers.normalize(message.address)
            dao.upsert(
                MessageEntity(
                    localId = message.clientMessageId ?: "h${message.hostSmsId}",
                    hostSmsId = message.hostSmsId,
                    threadKey = threadKey,
                    address = threadKey,
                    body = message.body,
                    direction = message.direction,
                    status = message.status,
                    timestampMs = message.timestampMs,
                    read = message.direction == SmsDirection.OUTBOUND,
                ),
            )
            message.direction == SmsDirection.INBOUND
        }
    }

    suspend fun applyStatus(update: SmsStatusUpdate) {
        val localId = update.clientMessageId ?: dao.findByHostId(update.hostSmsId)?.localId ?: return
        advance(localId, update.status, hostSmsId = update.hostSmsId)
    }

    suspend fun clear() = dao.clear()

    private suspend fun advance(localId: String, status: SmsStatus, hostSmsId: Long? = null, errorText: String? = null) {
        db.withTransaction {
            val current = dao.find(localId) ?: return@withTransaction
            dao.upsert(
                current.copy(
                    hostSmsId = hostSmsId ?: current.hostSmsId,
                    status = merged(current.status, status),
                    errorText = errorText ?: current.errorText,
                ),
            )
        }
    }

    private fun merged(current: SmsStatus, incoming: SmsStatus) =
        if (current.canAdvanceTo(incoming)) incoming else current
}
