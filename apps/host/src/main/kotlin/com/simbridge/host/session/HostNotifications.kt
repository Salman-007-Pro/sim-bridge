package com.simbridge.host.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object HostNotifications {
    const val CHANNEL_BRIDGE = "bridge_status"
    const val CHANNEL_CALLS = "host_calls"
    const val CHANNEL_SMS = "host_sms"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_BRIDGE, "Bridge status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows when this phone shares calls and SMS with your Client phone"
                },
                NotificationChannel(CHANNEL_CALLS, "Calls on this phone", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_SMS, "SMS on this phone", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }
}
