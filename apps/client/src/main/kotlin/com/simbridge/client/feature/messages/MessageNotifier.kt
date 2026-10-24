package com.simbridge.client.feature.messages

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.simbridge.client.MainActivity
import com.simbridge.client.R
import com.simbridge.client.data.ClientDatabase
import com.simbridge.client.data.ClientPrefs
import com.simbridge.client.data.ContactNames
import com.simbridge.client.session.ClientNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: ClientDatabase,
    private val prefs: ClientPrefs,
    private val contacts: ContactNames,
) {
    suspend fun notifyThread(threadKey: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val unread = db.messages().unreadInThread(threadKey, 6).reversed()
        if (unread.isEmpty()) return
        val hide = prefs.isHidingContent()
        val sender = Person.Builder().setName(contacts.displayName(threadKey)).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        unread.forEach { message ->
            style.addMessage(if (hide) OtpMasking.mask(message.body) else message.body, message.timestampMs, sender)
        }
        val open = PendingIntent.getActivity(
            context,
            threadKey.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_THREAD, threadKey)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, ClientNotifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setContentTitle("New message")
            .setContentText("Unlock to read")
            .build()
        val notification = NotificationCompat.Builder(context, ClientNotifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(if (hide) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(public)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(threadKey.hashCode(), notification) }
    }

    fun clear(threadKey: String) = NotificationManagerCompat.from(context).cancel(threadKey.hashCode())
}
