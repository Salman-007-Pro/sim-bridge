package com.simbridge.host.sms

import androidx.room.withTransaction
import com.simbridge.core.model.Clock
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import com.simbridge.host.data.HostDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Host-side SMS record. Every change bumps the sync clock so Clients can resume from a checkpoint. */
@Singleton
class HostSmsRepository @Inject constructor(
    private val db: HostDatabase,
    private val clock: Clock,
) {
    private val dao = db.sms()
    private val mutex = Mutex()

    fun recent(limit: Int = 100): Flow<List<SmsEntity>> = dao.recent(limit)

    fun thread(address: String): Flow<List<SmsEntity>> = dao.thread(PhoneNumbers.normalize(address))

    suspend fun findByClientId(clientMessageId: String): SmsEntity? = dao.findByClientId(clientMessageId)

    suspend fun insertInbound(address: String, body: String, providerUri: String?): SmsEntity = mutex.withLock {
        db.withTransaction {
            val entity = SmsEntity(
                clientMessageId = null,
                address = PhoneNumbers.normalize(address),
                body = body,
                direction = SmsDirection.INBOUND,
                status = SmsStatus.RECEIVED,
                timestampMs = clock.now(),
                providerUri = providerUri,
                changeSeq = db.syncClock().next(),
            )
            entity.copy(id = dao.insertIgnore(entity))
        }
    }

    /** Atomically claims an outbound send. Returns null if this clientMessageId was already claimed. */
    suspend fun claimOutbound(clientMessageId: String?, address: String, body: String, parts: Int): SmsEntity? = mutex.withLock {
        db.withTransaction {
            val entity = SmsEntity(
                clientMessageId = clientMessageId,
                address = PhoneNumbers.normalize(address),
                body = body,
                direction = SmsDirection.OUTBOUND,
                status = SmsStatus.QUEUED,
                timestampMs = clock.now(),
                partsTotal = parts.coerceAtLeast(1),
                changeSeq = db.syncClock().next(),
            )
            val id = dao.insertIgnore(entity)
            if (id == -1L) null else entity.copy(id = id)
        }
    }

    /** Applies [transform]; returns the stored row only if something changed. Status never moves backwards. */
    suspend fun update(id: Long, transform: (SmsEntity) -> SmsEntity): SmsEntity? = mutex.withLock {
        db.withTransaction {
            val current = dao.find(id) ?: return@withTransaction null
            val next = transform(current)
            val statusOk = next.status == current.status || current.status.canAdvanceTo(next.status)
            val candidate = if (statusOk) next else next.copy(status = current.status, errorCode = current.errorCode)
            if (candidate == current) return@withTransaction null
            candidate.copy(changeSeq = db.syncClock().next()).also { dao.update(it) }
        }
    }

    suspend fun onPartSent(id: Long, ok: Boolean, errorCode: Int?): SmsEntity? = update(id) { sms ->
        when {
            !ok -> sms.copy(status = SmsStatus.FAILED, errorCode = errorCode)
            else -> {
                val sent = (sms.partsSent + 1).coerceAtMost(sms.partsTotal)
                sms.copy(partsSent = sent, status = if (sent == sms.partsTotal) SmsStatus.SENT else sms.status)
            }
        }
    }

    suspend fun onPartDelivered(id: Long, ok: Boolean, errorCode: Int?): SmsEntity? = update(id) { sms ->
        when {
            !ok -> sms.copy(status = SmsStatus.FAILED, errorCode = errorCode)
            else -> {
                val delivered = (sms.partsDelivered + 1).coerceAtMost(sms.partsTotal)
                sms.copy(partsDelivered = delivered, status = if (delivered == sms.partsTotal) SmsStatus.DELIVERED else sms.status)
            }
        }
    }

    suspend fun changesSince(sinceSeq: Long, limit: Int): List<SmsEntity> = dao.changesSince(sinceSeq, limit)

    suspend fun clear() = dao.clear()
}
