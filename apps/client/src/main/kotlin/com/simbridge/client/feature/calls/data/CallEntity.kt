package com.simbridge.client.feature.calls.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo
import kotlinx.coroutines.flow.Flow

/** Projection of the Host's call record; the Host owns [callId] and the state. */
@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val callId: String,
    val number: String?,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAtMs: Long,
    val connectedAtMs: Long?,
    val endedAtMs: Long?,
    val disconnectReason: String?,
    val seen: Boolean = true,
) {
    val missed: Boolean get() = direction == CallDirection.INCOMING && connectedAtMs == null && status.isTerminal

    fun toInfo() = CallInfo(callId, number, direction, status, startedAtMs, connectedAtMs, endedAtMs, disconnectReason)

    companion object {
        fun from(info: CallInfo, seen: Boolean) = CallEntity(
            info.callId, info.number, info.direction, info.status, info.startedAtMs,
            info.connectedAtMs, info.endedAtMs, info.disconnectReason, seen,
        )
    }
}

@Dao
interface CallDao {
    @Upsert
    suspend fun upsert(call: CallEntity)

    @Query("SELECT * FROM calls WHERE callId = :callId")
    suspend fun find(callId: String): CallEntity?

    @Query("SELECT * FROM calls ORDER BY startedAtMs DESC LIMIT 300")
    fun history(): Flow<List<CallEntity>>

    @Query("UPDATE calls SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("DELETE FROM calls")
    suspend fun clear()
}
