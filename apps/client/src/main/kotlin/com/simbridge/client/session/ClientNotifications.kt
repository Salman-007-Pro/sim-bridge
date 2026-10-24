package com.simbridge.client.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object ClientNotifications {
    const val CHANNEL_CONNECTION = "connection"
    const val CHANNEL_INCOMING_CALLS = "incoming_calls"
    const val CHANNEL_ONGOING_CALL = "ongoing_call"
    const val CHANNEL_MESSAGES = "messages"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CONNECTION, "Connection to Host", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps the link to your Host phone open so calls and SMS arrive"
                },
                NotificationChannel(CHANNEL_INCOMING_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(ringtone, ringAttributes)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ONGOING_CALL, "Ongoing call", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                },
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            ),
        )
    }
}
