package com.simbridge.core.storage.ledger

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "command_ledger")
data class CommandRecord(
    @PrimaryKey val key: String,
    val commandType: String,
    val outcome: String?,
    val createdAtMs: Long,
)

@Dao
abstract class LedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(record: CommandRecord): Long

    @Query("SELECT * FROM command_ledger WHERE `key` = :key")
    abstract suspend fun find(key: String): CommandRecord?

    @Query("UPDATE command_ledger SET outcome = :outcome WHERE `key` = :key")
    abstract suspend fun setOutcome(key: String, outcome: String)

    @Query("DELETE FROM command_ledger WHERE createdAtMs < :olderThanMs")
    abstract suspend fun prune(olderThanMs: Long)

    @Query("DELETE FROM command_ledger")
    abstract suspend fun clear()

    @Transaction
    open suspend fun claim(record: CommandRecord): CommandRecord? =
        if (insert(record) == -1L) find(record.key) else null
}

@Database(entities = [CommandRecord::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        fun create(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "command_ledger.db").build()
    }
}

class RoomCommandLedger(private val dao: LedgerDao) : CommandLedger {
    override suspend fun claim(key: String, commandType: String, nowMs: Long): LedgerClaim {
        val existing = dao.claim(CommandRecord(key, commandType, null, nowMs))
        return if (existing == null) LedgerClaim.New else LedgerClaim.Duplicate(existing.outcome)
    }

    override suspend fun complete(key: String, outcome: String) = dao.setOutcome(key, outcome)

    override suspend fun prune(olderThanMs: Long) = dao.prune(olderThanMs)

    override suspend fun clear() = dao.clear()
}
