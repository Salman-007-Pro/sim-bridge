package com.simbridge.host.di

import android.content.Context
import com.simbridge.core.crypto.AndroidKeystoreIdentityStore
import com.simbridge.core.model.Clock
import com.simbridge.core.storage.ledger.CommandLedger
import com.simbridge.core.storage.ledger.LedgerDatabase
import com.simbridge.core.storage.ledger.RoomCommandLedger
import com.simbridge.core.storage.pairing.PairedDeviceStore
import com.simbridge.host.navigation.HostTool
import com.simbridge.host.session.HostCommandHandler
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
object HostModule {
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
        AndroidKeystoreIdentityStore(alias = "simbridge_host_identity", commonName = "SIM Bridge Host")

    @Provides
    @Singleton
    fun pairedClients(@ApplicationContext context: Context): PairedDeviceStore =
        PairedDeviceStore.create(context, "host_paired_clients")

    @Provides
    @Singleton
    fun ledgerDatabase(@ApplicationContext context: Context): LedgerDatabase = LedgerDatabase.create(context)

    @Provides
    @Singleton
    fun commandLedger(db: LedgerDatabase): CommandLedger = RoomCommandLedger(db.ledgerDao())
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HostHandlerModule {
    @Multibinds
    abstract fun commandHandlers(): Set<HostCommandHandler>

    @Multibinds
    abstract fun dashboardTools(): Set<HostTool>
}
