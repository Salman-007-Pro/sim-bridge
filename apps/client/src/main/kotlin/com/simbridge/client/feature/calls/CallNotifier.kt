package com.simbridge.client.feature.calls

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.simbridge.client.R
import com.simbridge.client.data.ContactNames
import com.simbridge.client.di.ApplicationScope
import com.simbridge.client.session.ClientNotifications
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Incoming calls ring as a full-screen call notification; ongoing calls keep a quiet notification with Hang up. */
@Singleton
class CallNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contacts: ContactNames,
) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun render(primary: CallInfo?) {
        if (!canNotify()) return
        when {
            primary == null || primary.status.isTerminal -> {
                manager.cancel(INCOMING_ID)
                manager.cancel(ONGOING_ID)
            }
            primary.status == CallStatus.RINGING -> {
                manager.cancel(ONGOING_ID)
                runCatching { manager.notify(INCOMING_ID, incoming(primary)) }
            }
            else -> {
                manager.cancel(INCOMING_ID)
                runCatching { manager.notify(ONGOING_ID, ongoing(primary)) }
            }
        }
    }

    fun missed(call: CallInfo) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, ClientNotifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setContentTitle("Missed call")
            .setContentText(contacts.displayName(call.number))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(call.callId.hashCode(), notification) }
    }

    private fun incoming(call: CallInfo): Notification {
        val caller = Person.Builder().setName(contacts.displayName(call.number)).setImportant(true).build()
        val fullScreen = inCallIntent()
        return NotificationCompat.Builder(context, ClientNotifications.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    action(CallActionReceiver.ACTION_REJECT, call.callId),
                    action(CallActionReceiver.ACTION_ANSWER, call.callId),
                ),
            )
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
    }

    private fun ongoing(call: CallInfo): Notification {
        val text = when (call.status) {
            CallStatus.HOST_UNREACHABLE -> "Host unreachable — the call may still be active on the Host"
            CallStatus.ACTIVE -> "In call · audio is on your Host phone"
            CallStatus.HOLD -> "On hold"
            else -> "Calling via your Host phone…"
        }
        return NotificationCompat.Builder(context, ClientNotifications.CHANNEL_ONGOING_CALL)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentTitle(contacts.displayName(call.number))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(call.status == CallStatus.ACTIVE)
            .setWhen(call.connectedAtMs ?: call.startedAtMs)
            .setContentIntent(inCallIntent())
            .addAction(0, "Hang up", action(CallActionReceiver.ACTION_HANGUP, call.callId))
            .build()
    }

    private fun canNotify() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun inCallIntent(): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 2, context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, callId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (action + callId).hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val INCOMING_ID = 3_001
        const val ONGOING_ID = 3_002
    }
}

@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: CallController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val pending = goAsync()
        if (intent.action == ACTION_ANSWER) {
            context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_ANSWER -> controller.answer(callId)
                    ACTION_REJECT -> controller.reject(callId)
                    ACTION_HANGUP -> controller.hangup(callId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val ACTION_ANSWER = "com.simbridge.client.call.ANSWER"
        const val ACTION_REJECT = "com.simbridge.client.call.REJECT"
        const val ACTION_HANGUP = "com.simbridge.client.call.HANGUP"
    }
}
