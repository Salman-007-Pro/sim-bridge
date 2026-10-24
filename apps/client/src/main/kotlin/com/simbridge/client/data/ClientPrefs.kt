package com.simbridge.client.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Endpoint(val address: String, val port: Int)

@Singleton
class ClientPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("client_prefs") }
    private val manualEndpoint = stringPreferencesKey("manual_endpoint")
    private val smsCheckpoint = longPreferencesKey("sms_checkpoint")
    private val callCheckpoint = longPreferencesKey("call_checkpoint")
    private val syncEpochKey = stringPreferencesKey("sync_epoch")
    private val hideMessageContent = booleanPreferencesKey("hide_message_content")

    val manualEndpointText: Flow<String?> = store.data.map { it[manualEndpoint] }
    val hideContentOnLockScreen: Flow<Boolean> = store.data.map { it[hideMessageContent] ?: true }

    suspend fun manualEndpoint(): Endpoint? = store.data.first()[manualEndpoint]?.let(::parseEndpoint)

    suspend fun setManualEndpoint(text: String?) = store.edit { prefs ->
        if (text.isNullOrBlank()) prefs.remove(manualEndpoint) else prefs[manualEndpoint] = text.trim()
    }

    suspend fun smsCheckpoint(): Long = store.data.first()[smsCheckpoint] ?: 0L
    suspend fun callCheckpoint(): Long = store.data.first()[callCheckpoint] ?: 0L
    suspend fun setCheckpoints(sms: Long, calls: Long) = store.edit {
        it[smsCheckpoint] = sms
        it[callCheckpoint] = calls
    }

    suspend fun isHidingContent(): Boolean = hideContentOnLockScreen.first()
    suspend fun setHideContent(hide: Boolean) = store.edit { it[hideMessageContent] = hide }

    suspend fun syncEpoch(): String? = store.data.first()[syncEpochKey]
    suspend fun setSyncEpoch(epoch: String) = store.edit { it[syncEpochKey] = epoch }

    suspend fun clearSyncState() = store.edit {
        it.remove(smsCheckpoint)
        it.remove(callCheckpoint)
        it.remove(syncEpochKey)
    }

    suspend fun clearAll() = store.edit { it.clear() }

    companion object {
        /** Accepts "192.168.1.5" or "192.168.1.5:47820"; IPv4 literals only. */
        fun parseEndpoint(text: String): Endpoint? {
            val parts = text.trim().split(":")
            val address = parts[0]
            if (!com.simbridge.core.crypto.PairingPayload.isIpv4Literal(address)) return null
            val port = parts.getOrNull(1)?.toIntOrNull() ?: com.simbridge.core.transport.BridgeServer.DEFAULT_PORT
            return if (parts.size <= 2 && port in 1024..65535) Endpoint(address, port) else null
        }
    }
}
