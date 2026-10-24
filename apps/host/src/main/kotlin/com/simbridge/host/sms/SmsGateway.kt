package com.simbridge.host.sms

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.model.Redaction
import com.simbridge.core.model.SmsStatus
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsStatusUpdate
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.host.feature.permissions.HostPermissions
import com.simbridge.host.session.HostBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SmsSendResult {
    data class Accepted(val sms: SmsEntity) : SmsSendResult
    data class Duplicate(val sms: SmsEntity) : SmsSendResult
    data class Refused(val code: ErrorCode, val message: String) : SmsSendResult
}

/** Sends real carrier SMS through the platform and tracks every part's sent / delivered report. */
@Singleton
class SmsGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: HostSmsRepository,
    private val provider: TelephonyProviderWriter,
    private val bridge: HostBridge,
) {
    suspend fun send(clientMessageId: String?, to: String, text: String): SmsSendResult {
        if (!PhoneNumbers.isDialable(to)) return SmsSendResult.Refused(ErrorCode.INVALID_NUMBER, "Recipient is not a valid number")
        if (text.isEmpty() || text.length > MAX_TEXT) return SmsSendResult.Refused(ErrorCode.MALFORMED, "Message is empty or too long")
        if (!HostPermissions.isRoleHeld(context, RoleManager.ROLE_SMS)) {
            return SmsSendResult.Refused(ErrorCode.ROLE_MISSING, "SIM Bridge Host is not the default SMS app on the Host phone")
        }
        if (!HostPermissions.granted(context, Manifest.permission.SEND_SMS)) {
            return SmsSendResult.Refused(ErrorCode.PERMISSION_MISSING, "SMS permission is not granted on the Host phone")
        }

        val manager = smsManager()
        val parts = manager.divideMessage(text)
        val claimed = repository.claimOutbound(clientMessageId, to, text, parts.size)
        if (claimed == null) {
            val existing = clientMessageId?.let { repository.findByClientId(it) }
                ?: return SmsSendResult.Refused(ErrorCode.INTERNAL, "Duplicate message could not be found")
            return SmsSendResult.Duplicate(existing)
        }

        val providerUri = provider.insertOutbox(claimed.address, text, claimed.timestampMs)
        val result = try {
            val sent = ArrayList(parts.indices.map { pendingIntent(claimed.id, it, SmsStatusReceiver.KIND_SENT) })
            val delivered = ArrayList(parts.indices.map { pendingIntent(claimed.id, it, SmsStatusReceiver.KIND_DELIVERED) })
            manager.sendMultipartTextMessage(claimed.address, null, parts, sent, delivered)
            repository.update(claimed.id) { it.copy(status = SmsStatus.SUBMITTED_TO_MODEM, providerUri = providerUri) }
        } catch (e: Exception) {
            BridgeLog.e(TAG, "sendMultipartTextMessage failed for ${Redaction.phone(claimed.address)}", e)
            provider.markFailed(providerUri)
            repository.update(claimed.id) { it.copy(status = SmsStatus.FAILED, errorCode = ERROR_PLATFORM, providerUri = providerUri) }
        }
        val current = result ?: claimed
        publish(current)
        BridgeLog.i(TAG, "SMS ${current.id} to ${Redaction.phone(current.address)} ${Redaction.body(text)} -> ${current.status}")
        return SmsSendResult.Accepted(current)
    }

    suspend fun onPartReport(id: Long, kind: String, ok: Boolean, errorCode: Int?) {
        val updated = if (kind == SmsStatusReceiver.KIND_SENT) {
            repository.onPartSent(id, ok, errorCode)
        } else {
            repository.onPartDelivered(id, ok, errorCode)
        } ?: return
        when (updated.status) {
            SmsStatus.SENT -> provider.markSent(updated.providerUri)
            SmsStatus.DELIVERED -> provider.markDelivered(updated.providerUri)
            SmsStatus.FAILED -> provider.markFailed(updated.providerUri)
            else -> Unit
        }
        publish(updated)
    }

    suspend fun publish(sms: SmsEntity) {
        bridge.publish(MessageType.SMS_STATUS, SmsStatusUpdate.serializer(), SmsStatusUpdate(sms.id, sms.clientMessageId, sms.status, sms.errorCode))
    }

    private fun pendingIntent(id: Long, part: Int, kind: String): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
            .setAction("com.simbridge.host.SMS_$kind")
            .putExtra(SmsStatusReceiver.EXTRA_ID, id)
            .putExtra(SmsStatusReceiver.EXTRA_PART, part)
            .putExtra(SmsStatusReceiver.EXTRA_KIND, kind)
        val requestCode = ((id * 64 + part * 2 + if (kind == SmsStatusReceiver.KIND_SENT) 0 else 1) % Int.MAX_VALUE).toInt()
        // Mutable so the platform can attach the delivery PDU; the intent is explicit to our own receiver.
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    private companion object {
        const val TAG = "SmsGateway"
        const val MAX_TEXT = 5_000
        const val ERROR_PLATFORM = -1
    }
}
