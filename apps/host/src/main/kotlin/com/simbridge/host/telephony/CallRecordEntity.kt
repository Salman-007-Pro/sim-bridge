package com.simbridge.host.telephony

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo

@Entity(tableName = "call_records", indices = [Index("changeSeq")])
data class CallRecordEntity(
    @PrimaryKey val callId: String,
    val number: String?,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAtMs: Long,
    val connectedAtMs: Long?,
    val endedAtMs: Long?,
    val disconnectReason: String?,
    val changeSeq: Long,
) {
    fun toProtocol() = CallInfo(callId, number, direction, status, startedAtMs, connectedAtMs, endedAtMs, disconnectReason)
}

@Dao
interface CallRecordDao {
    @Upsert
    suspend fun upsert(entity: CallRecordEntity)

    @Query("SELECT * FROM call_records WHERE callId = :callId")
    suspend fun find(callId: String): CallRecordEntity?

    @Query("SELECT * FROM call_records WHERE changeSeq > :sinceSeq ORDER BY changeSeq ASC LIMIT :limit")
    suspend fun changesSince(sinceSeq: Long, limit: Int): List<CallRecordEntity>

    @Query("DELETE FROM call_records")
    suspend fun clear()
}
