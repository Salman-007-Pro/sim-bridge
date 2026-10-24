package com.simbridge.core.storage.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Certificate fingerprints are public identifiers, not secrets; private keys stay in the Keystore. */
@Serializable
data class PairedDevice(
    val fingerprint: String,
    val name: String,
    val pairedAtMs: Long,
    val lastSeenAtMs: Long? = null,
    val lastAddress: String? = null,
    val lastPort: Int? = null,
)

class PairedDeviceStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("paired_devices")
    private val serializer = ListSerializer(PairedDevice.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    val devices: Flow<List<PairedDevice>> = dataStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun current(): List<PairedDevice> = devices.first()

    suspend fun find(fingerprint: String): PairedDevice? = current().firstOrNull { it.fingerprint == fingerprint }

    suspend fun upsert(device: PairedDevice) = update { list -> list.filterNot { it.fingerprint == device.fingerprint } + device }

    suspend fun touch(fingerprint: String, nowMs: Long, address: String? = null, port: Int? = null) = update { list ->
        list.map {
            if (it.fingerprint != fingerprint) it
            else it.copy(lastSeenAtMs = nowMs, lastAddress = address ?: it.lastAddress, lastPort = port ?: it.lastPort)
        }
    }

    suspend fun remove(fingerprint: String) = update { list -> list.filterNot { it.fingerprint == fingerprint } }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(transform: (List<PairedDevice>) -> List<PairedDevice>) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(serializer, transform(decode(prefs[key]))) }
    }

    private fun decode(raw: String?): List<PairedDevice> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    companion object {
        fun create(context: Context, fileName: String): PairedDeviceStore =
            PairedDeviceStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(fileName) })
    }
}
