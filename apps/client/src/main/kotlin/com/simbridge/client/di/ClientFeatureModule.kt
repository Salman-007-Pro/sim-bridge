package com.simbridge.client.di

import android.content.Context
import com.simbridge.client.data.ClientDatabase
import com.simbridge.client.feature.messages.MessagesHandler
import com.simbridge.client.feature.messages.SyncHandler
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientMessageHandler
import com.simbridge.client.session.HostLink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClientFeatureModule {
    @Binds
    abstract fun hostLink(bridge: ClientBridge): HostLink

    @Binds
    @IntoSet
    abstract fun messagesHandler(handler: MessagesHandler): ClientMessageHandler

    @Binds
    @IntoSet
    abstract fun syncHandler(handler: SyncHandler): ClientMessageHandler

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): ClientDatabase = ClientDatabase.create(context)
    }
}
