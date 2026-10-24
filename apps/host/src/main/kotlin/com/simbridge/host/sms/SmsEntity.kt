package com.simbridge.host.sms

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import com.simbridge.core.protocol.SmsMessage
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sms",
    indices = [Index(value = ["clientMessageId"], unique = true), Index("changeSeq"), Index("address")],
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientMessageId: String?,
    val address: String,
    val body: String,
    val direction: SmsDirection,
    val status: SmsStatus,
    val timestampMs: Long,
    val partsTotal: Int = 1,
    val partsSent: Int = 0,
    val partsDelivered: Int = 0,
    val errorCode: Int? = null,
    val providerUri: String? = null,
    val changeSeq: Long,
) {
    fun toProtocol() = SmsMessage(id, clientMessageId, address, body, direction, status, timestampMs, errorCode)
}

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SmsEntity): Long

    @Update
    suspend fun update(entity: SmsEntity)

    @Query("SELECT * FROM sms WHERE id = :id")
    suspend fun find(id: Long): SmsEntity?

    @Query("SELECT * FROM sms WHERE clientMessageId = :clientMessageId")
    suspend fun findByClientId(clientMessageId: String): SmsEntity?

    @Query("SELECT * FROM sms WHERE changeSeq > :sinceSeq ORDER BY changeSeq ASC LIMIT :limit")
    suspend fun changesSince(sinceSeq: Long, limit: Int): List<SmsEntity>

    @Query("SELECT * FROM sms ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms WHERE address = :address ORDER BY timestampMs ASC")
    fun thread(address: String): Flow<List<SmsEntity>>

    @Query("DELETE FROM sms")
    suspend fun clear()
}
