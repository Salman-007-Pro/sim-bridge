package com.simbridge.host.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class HostPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("host_prefs") }
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val bridgeEnabledKey = booleanPreferencesKey("bridge_enabled")
    private val syncEpochKey = stringPreferencesKey("sync_epoch")

    val isOnboardingDone: Flow<Boolean> = store.data.map { it[onboardingDoneKey] ?: false }

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[onboardingDoneKey] = done }

    suspend fun isBridgeEnabled(): Boolean = store.data.first()[bridgeEnabledKey] ?: false

    suspend fun setBridgeEnabled(enabled: Boolean) = store.edit { it[bridgeEnabledKey] = enabled }

    /** Identifies the current SMS / call store; replaced whenever that store is wiped. */
    suspend fun syncEpoch(): String {
        store.edit { if (it[syncEpochKey] == null) it[syncEpochKey] = UUID.randomUUID().toString() }
        return store.data.first()[syncEpochKey] ?: error("sync epoch missing after write")
    }

    suspend fun resetSyncEpoch() = store.edit { it[syncEpochKey] = UUID.randomUUID().toString() }
}
