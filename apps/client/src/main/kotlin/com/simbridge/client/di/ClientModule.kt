package com.simbridge.client.di

import android.content.Context
import com.simbridge.client.session.ClientMessageHandler
import com.simbridge.core.crypto.AndroidKeystoreIdentityStore
import com.simbridge.core.model.Clock
import com.simbridge.core.storage.pairing.PairedDeviceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ClientModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun clock(): Clock = Clock.System

    @Provides
    @Singleton
    fun identityStore(): AndroidKeystoreIdentityStore =
        AndroidKeystoreIdentityStore(alias = "simbridge_client_identity", commonName = "SIM Bridge Client")

    @Provides
    @Singleton
    fun pairedHost(@ApplicationContext context: Context): PairedDeviceStore =
        PairedDeviceStore.create(context, "client_paired_host")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClientHandlerModule {
    @Multibinds
    abstract fun messageHandlers(): Set<ClientMessageHandler>
}
