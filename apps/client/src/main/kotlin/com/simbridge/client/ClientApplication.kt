package com.simbridge.client

import android.app.Application
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientMessageHandler
import com.simbridge.client.session.ClientNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ClientApplication : Application() {
    @Inject lateinit var bridge: ClientBridge
    @Inject lateinit var handlers: Set<@JvmSuppressWildcards ClientMessageHandler>

    override fun onCreate() {
        super.onCreate()
        ClientNotifications.createChannels(this)
        handlers.forEach(bridge::register)
        bridge.initialize()
    }
}
