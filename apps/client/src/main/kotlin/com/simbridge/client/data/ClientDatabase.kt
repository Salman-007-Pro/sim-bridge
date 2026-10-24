package com.simbridge.client.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simbridge.client.feature.calls.data.CallDao
import com.simbridge.client.feature.calls.data.CallEntity
import com.simbridge.client.feature.messages.data.MessageDao
import com.simbridge.client.feature.messages.data.MessageEntity

@Database(entities = [MessageEntity::class, CallEntity::class], version = 1, exportSchema = true)
abstract class ClientDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun calls(): CallDao

    companion object {
        fun create(context: Context): ClientDatabase =
            Room.databaseBuilder(context, ClientDatabase::class.java, "client.db").build()
    }
}
