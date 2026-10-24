package com.simbridge.host.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.simbridge.core.storage.logging.BridgeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * As the default SMS app the Host must persist messages in the system SMS provider,
 * otherwise they would be missing from the phone once another SMS app is made default again.
 */
@Singleton
class TelephonyProviderWriter @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun insertInbox(address: String, body: String, sentAtMs: Long, receivedAtMs: Long, subscriptionId: Int?): String? =
        insert(Telephony.Sms.Inbox.CONTENT_URI) {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAtMs)
            put(Telephony.Sms.DATE_SENT, sentAtMs)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }

    fun insertOutbox(address: String, body: String, atMs: Long): String? =
        insert(Telephony.Sms.Outbox.CONTENT_URI) {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, atMs)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }

    fun markSent(uri: String?) = update(uri) { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }

    fun markFailed(uri: String?) = update(uri) {
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
    }

    fun markDelivered(uri: String?) = update(uri) { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) }

    private fun insert(target: Uri, fill: ContentValues.() -> Unit): String? = runCatching {
        context.contentResolver.insert(target, ContentValues().apply(fill))?.toString()
    }.onFailure { BridgeLog.w(TAG, "SMS provider insert failed", it) }.getOrNull()

    private fun update(uri: String?, fill: ContentValues.() -> Unit) {
        if (uri == null) return
        runCatching { context.contentResolver.update(Uri.parse(uri), ContentValues().apply(fill), null, null) }
            .onFailure { BridgeLog.w(TAG, "SMS provider update failed", it) }
    }

    private companion object {
        const val TAG = "SmsProvider"
    }
}
