package com.simbridge.host.di

import com.simbridge.host.navigation.HostTool
import com.simbridge.host.session.HostCommandHandler
import com.simbridge.host.sms.HostMessagesRoute
import com.simbridge.host.sms.HostMessagesScreen
import com.simbridge.host.sms.SmsCommandHandler
import com.simbridge.host.telephony.CallCommandHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class HostFeatureModule {
    @Binds
    @IntoSet
    abstract fun smsHandler(handler: SmsCommandHandler): HostCommandHandler

    @Binds
    @IntoSet
    abstract fun callHandler(handler: CallCommandHandler): HostCommandHandler

    companion object {
        @Provides
        @IntoSet
        fun messagesTool(): HostTool = HostTool("Messages on this phone", HostMessagesRoute) { onBack -> HostMessagesScreen(onBack) }
    }
}
