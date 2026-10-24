package com.simbridge.host

import android.app.Application
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostCommandHandler
import com.simbridge.host.session.HostNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HostApplication : Application() {
    @Inject lateinit var bridge: HostBridge
    @Inject lateinit var handlers: Set<@JvmSuppressWildcards HostCommandHandler>

    override fun onCreate() {
        super.onCreate()
        HostNotifications.createChannels(this)
        handlers.forEach(bridge::register)
    }
}
