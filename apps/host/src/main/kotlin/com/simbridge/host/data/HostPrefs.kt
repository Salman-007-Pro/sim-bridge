package com.simbridge.host.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class HostPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("host_prefs") }
    private val onboardingDone = booleanPreferencesKey("onboarding_done")
    private val bridgeEnabled = booleanPreferencesKey("bridge_enabled")

    val isOnboardingDone: Flow<Boolean> = store.data.map { it[onboardingDone] ?: false }

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[onboardingDone] = done }

    suspend fun isBridgeEnabled(): Boolean = store.data.first()[bridgeEnabled] ?: false

    suspend fun setBridgeEnabled(enabled: Boolean) = store.edit { it[bridgeEnabled] = enabled }
}
