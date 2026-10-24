package com.simbridge.client.feature.calls.data

import com.simbridge.client.data.ClientDatabase
import com.simbridge.core.protocol.CallInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class CallHistoryRepository @Inject constructor(db: ClientDatabase) {
    private val dao = db.calls()

    fun history(): Flow<List<CallEntity>> = dao.history()

    /** The Host is authoritative: its latest record replaces the local one, unless it would regress a final state. */
    suspend fun apply(info: CallInfo): CallEntity {
        val existing = dao.find(info.callId)
        if (existing != null && existing.status.isTerminal && !info.status.isTerminal) return existing
        val next = CallEntity.from(info, seen = existing?.seen ?: true)
        val entity = if (next.missed && existing?.missed != true) next.copy(seen = false) else next
        dao.upsert(entity)
        return entity
    }

    suspend fun markAllSeen() = dao.markAllSeen()

    suspend fun clear() = dao.clear()
}
