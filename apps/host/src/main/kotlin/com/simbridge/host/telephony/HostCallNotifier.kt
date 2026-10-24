package com.simbridge.host.telephony

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo
import com.simbridge.host.R
import com.simbridge.host.di.ApplicationScope
import com.simbridge.host.session.HostNotifications
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Host-side call notifications. Telecom plays the ringtone; this provides the visible UI. */
@Singleton
class HostCallNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: CallRegistry,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { registry.calls.collect(::render) }
    }

    fun stop() {
        job?.cancel()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun render(calls: List<CallInfo>) {
        val manager = NotificationManagerCompat.from(context)
        val call = calls.firstOrNull { it.status == CallStatus.RINGING } ?: calls.lastOrNull()
        if (call == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        runCatching { manager.notify(NOTIFICATION_ID, build(call)) }
    }

    private fun build(call: CallInfo): Notification {
        val caller = Person.Builder().setName(call.number ?: "Private number").setImportant(true).build()
        val fullScreen = PendingIntent.getActivity(
            context, 0, Intent(context, HostInCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringing = call.direction == CallDirection.INCOMING && call.status == CallStatus.RINGING
        val builder = NotificationCompat.Builder(context, HostNotifications.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, ringing)
        return if (ringing) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    action(CallActionReceiver.ACTION_REJECT, call.callId),
                    action(CallActionReceiver.ACTION_ANSWER, call.callId),
                ),
            ).build()
        } else {
            builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, action(CallActionReceiver.ACTION_HANGUP, call.callId)))
                .setContentText(call.status.name.lowercase().replaceFirstChar { it.uppercase() })
                .setUsesChronometer(call.status == CallStatus.ACTIVE)
                .setWhen(call.connectedAtMs ?: call.startedAtMs)
                .setSilent(true)
                .build()
        }
    }

    private fun action(action: String, callId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (action + callId).hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val NOTIFICATION_ID = 2_000
    }
}

@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var registry: CallRegistry

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_ANSWER -> {
                registry.answer(callId)
                context.startActivity(Intent(context, HostInCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ACTION_REJECT -> registry.reject(callId)
            ACTION_HANGUP -> registry.hangup(callId)
        }
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val ACTION_ANSWER = "com.simbridge.host.call.ANSWER"
        const val ACTION_REJECT = "com.simbridge.host.call.REJECT"
        const val ACTION_HANGUP = "com.simbridge.host.call.HANGUP"
    }
}
