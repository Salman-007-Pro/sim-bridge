package com.simbridge.host.sms

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat
import com.simbridge.core.model.Redaction
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsMessage as WireSmsMessage
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.host.R
import com.simbridge.host.di.ApplicationScope
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Incoming SMS (delivered only to the default SMS app). Stored first, then forwarded to the Client. */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: HostSmsRepository
    @Inject lateinit var provider: TelephonyProviderWriter
    @Inject lateinit var bridge: HostBridge
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sentAt = parts.first().timestampMillis
        val subId = intent.getIntExtra("subscription", -1).takeIf { it >= 0 }
        bridge.ensureServiceRunning()
        runAsync(scope) {
            val now = System.currentTimeMillis()
            val uri = provider.insertInbox(address, body, sentAt, now, subId)
            val stored = repository.insertInbound(address, body, uri)
            val forwarded = bridge.publish(MessageType.SMS_RECEIVED, WireSmsMessage.serializer(), stored.toProtocol())
            BridgeLog.i(TAG, "SMS from ${Redaction.phone(address)} ${Redaction.body(body)} forwarded=$forwarded")
            notifyOnHost(context, stored)
        }
    }

    private fun notifyOnHost(context: Context, sms: SmsEntity) {
        val open = PendingIntent.getActivity(
            context,
            sms.address.hashCode(),
            Intent(context, HostComposeActivity::class.java).setData(android.net.Uri.parse("smsto:${sms.address}")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, HostNotifications.CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(sms.address)
            .setContentText(sms.body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, HostNotifications.CHANNEL_SMS)
                    .setSmallIcon(R.drawable.ic_stat_bridge)
                    .setContentTitle("New message")
                    .build(),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(sms.address.hashCode(), notification)
    }

    private companion object {
        const val TAG = "SmsDeliver"
    }
}

/** MMS is not supported. The default SMS app must still accept WAP push, so tell the user honestly. */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val notification = NotificationCompat.Builder(context, HostNotifications.CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("MMS received")
            .setContentText("SIM Bridge cannot download MMS. Switch the default SMS app to view it.")
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(MMS_NOTIFICATION_ID, notification)
        BridgeLog.w("MmsWapPush", "MMS notification received but MMS is unsupported")
    }

    private companion object {
        const val MMS_NOTIFICATION_ID = 4_000
    }
}

/** Per-part sent and delivery reports for messages sent by [SmsGateway]. */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var gateway: SmsGateway
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val (ok, error) = if (kind == KIND_SENT) {
            (resultCode == Activity.RESULT_OK) to resultCode.takeIf { it != Activity.RESULT_OK }
        } else {
            deliveryStatus(intent) ?: return
        }
        runAsync(scope) { gateway.onPartReport(id, kind, ok, error) }
    }

    /**
     * GSM TP-Status: 0x00..0x1F delivered, 0x20..0x3F still trying (no update, null), >= 0x40 failed.
     * CDMA reports use a different encoding, so any CDMA report counts as delivered.
     */
    private fun deliveryStatus(intent: Intent): Pair<Boolean, Int?>? {
        val pdu = intent.getByteArrayExtra("pdu") ?: return true to null
        val format = intent.getStringExtra("format")
        if (format == "3gpp2") return true to null
        val status = runCatching { SmsMessage.createFromPdu(pdu, format).status }.getOrNull() ?: return true to null
        return when {
            status < 0x20 -> true to null
            status < 0x40 -> null
            else -> false to status
        }
    }

    companion object {
        const val EXTRA_ID = "sms_id"
        const val EXTRA_PART = "part"
        const val EXTRA_KIND = "kind"
        const val KIND_SENT = "SENT"
        const val KIND_DELIVERED = "DELIVERED"
    }
}
