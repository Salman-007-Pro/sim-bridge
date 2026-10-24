package com.simbridge.client.feature.messages.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import kotlinx.coroutines.flow.Flow

/**
 * [localId] is the clientMessageId for messages composed here, or "h<hostSmsId>" for messages
 * that originated on the Host. [hostSmsId] links both views of the same message.
 */
@Entity(
    tableName = "messages",
    indices = [Index("threadKey"), Index(value = ["hostSmsId"], unique = true), Index("status")],
)
data class MessageEntity(
    @PrimaryKey val localId: String,
    val hostSmsId: Long?,
    val threadKey: String,
    val address: String,
    val body: String,
    val direction: SmsDirection,
    val status: SmsStatus,
    val timestampMs: Long,
    val errorText: String? = null,
    val read: Boolean = true,
)

data class ThreadSummary(
    val threadKey: String,
    val address: String,
    val body: String,
    val direction: SmsDirection,
    val status: SmsStatus,
    val timestampMs: Long,
    val unread: Int,
)

@Dao
interface MessageDao {
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE localId = :localId")
    suspend fun find(localId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE hostSmsId = :hostSmsId")
    suspend fun findByHostId(hostSmsId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE threadKey = :threadKey ORDER BY timestampMs ASC, localId ASC")
    fun thread(threadKey: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT m.threadKey, m.address, m.body, m.direction, m.status, m.timestampMs,
            (SELECT COUNT(*) FROM messages u WHERE u.threadKey = m.threadKey AND u.read = 0) AS unread
        FROM messages m
        WHERE m.timestampMs = (SELECT MAX(x.timestampMs) FROM messages x WHERE x.threadKey = m.threadKey)
        GROUP BY m.threadKey
        ORDER BY m.timestampMs DESC
        """,
    )
    fun threads(): Flow<List<ThreadSummary>>

    @Query("SELECT * FROM messages WHERE direction = 'OUTBOUND' AND status = 'QUEUED' ORDER BY timestampMs ASC")
    suspend fun queued(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE threadKey = :threadKey AND read = 0 ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun unreadInThread(threadKey: String, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET read = 1 WHERE threadKey = :threadKey AND read = 0")
    suspend fun markThreadRead(threadKey: String)

    @Query("SELECT COUNT(*) FROM messages WHERE read = 0")
    fun unreadCount(): Flow<Int>

    @Query("DELETE FROM messages")
    suspend fun clear()
}
