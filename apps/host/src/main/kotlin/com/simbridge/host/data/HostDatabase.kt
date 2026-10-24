package com.simbridge.host.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.simbridge.host.sms.SmsDao
import com.simbridge.host.sms.SmsEntity
import com.simbridge.host.telephony.CallRecordDao
import com.simbridge.host.telephony.CallRecordEntity

/** Single-row monotonic counter; every SMS / call change gets the next value for checkpoint sync. */
@Entity(tableName = "sync_clock")
data class SyncClock(@PrimaryKey val id: Int = 0, val value: Long)

@Dao
abstract class SyncClockDao {
    @Query("INSERT OR IGNORE INTO sync_clock (id, value) VALUES (0, 0)")
    protected abstract suspend fun ensureRow()

    @Query("UPDATE sync_clock SET value = value + 1 WHERE id = 0")
    protected abstract suspend fun increment()

    @Query("SELECT value FROM sync_clock WHERE id = 0")
    protected abstract suspend fun current(): Long

    @Transaction
    open suspend fun next(): Long {
        ensureRow()
        increment()
        return current()
    }
}

@Database(
    entities = [SmsEntity::class, CallRecordEntity::class, SyncClock::class],
    version = 1,
    exportSchema = true,
)
abstract class HostDatabase : RoomDatabase() {
    abstract fun sms(): SmsDao
    abstract fun calls(): CallRecordDao
    abstract fun syncClock(): SyncClockDao

    companion object {
        fun create(context: Context): HostDatabase =
            Room.databaseBuilder(context, HostDatabase::class.java, "host.db").build()
    }
}
