package com.simbridge.client.feature.messages

import com.simbridge.client.data.ClientPrefs
import com.simbridge.client.feature.calls.data.CallHistoryRepository
import com.simbridge.client.feature.messages.data.MessagesRepository
import com.simbridge.client.session.ClientMessageHandler
import com.simbridge.client.session.HostLink
import com.simbridge.client.session.RequestOutcome
import com.simbridge.core.model.PhoneNumbers
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsMessage
import com.simbridge.core.protocol.SmsStatusUpdate
import com.simbridge.core.protocol.SyncDelta
import com.simbridge.core.protocol.SyncRequest
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.storage.logging.BridgeLog
import javax.inject.Inject
import javax.inject.Singleton

/** Live SMS events from the Host. */
@Singleton
class MessagesHandler @Inject constructor(
    private val repository: MessagesRepository,
    private val notifier: MessageNotifier,
) : ClientMessageHandler {
    override val types = setOf(MessageType.SMS_RECEIVED, MessageType.SMS_STATUS)

    override suspend fun handle(envelope: Envelope) {
        when (envelope.type) {
            MessageType.SMS_RECEIVED -> {
                val message = envelope.decodeBody(SmsMessage.serializer())
                if (repository.applyRemote(message)) notifier.notifyThread(PhoneNumbers.normalize(message.address))
            }
            MessageType.SMS_STATUS -> repository.applyStatus(envelope.decodeBody(SmsStatusUpdate.serializer()))
            else -> Unit
        }
    }

    override suspend fun onWipe() = repository.clear()
}

/**
 * On every (re)connect: pull everything changed since the last checkpoint (SMS and calls), then
 * flush queued SMS. Checkpoints restart from zero if the Host's store was recreated.
 */
@Singleton
class SyncHandler @Inject constructor(
    private val messages: MessagesRepository,
    private val calls: CallHistoryRepository,
    private val prefs: ClientPrefs,
    private val notifier: MessageNotifier,
) : ClientMessageHandler {
    override val types: Set<MessageType> = emptySet()

    override suspend fun handle(envelope: Envelope) = Unit

    override suspend fun onConnected(link: HostLink) {
        sync(link)
        messages.flushOutbox(link)
    }

    override suspend fun onWipe() {
        calls.clear()
        prefs.clearSyncState()
    }

    private suspend fun sync(link: HostLink) {
        var smsCheckpoint = prefs.smsCheckpoint()
        var callCheckpoint = prefs.callCheckpoint()
        val knownEpoch = prefs.syncEpoch()
        val threadsWithNewMessages = mutableSetOf<String>()
        repeat(MAX_PAGES) {
            val outcome = link.request(
                MessageType.SYNC_REQUEST,
                SyncRequest.serializer(),
                SyncRequest(smsCheckpoint, callCheckpoint, PAGE_SIZE),
                timeoutMs = 20_000,
            )
            val reply = (outcome as? RequestOutcome.Acked)?.reply?.takeIf { it.type == MessageType.SYNC_DELTA } ?: return
            val delta = reply.decodeBody(SyncDelta.serializer())
            if (knownEpoch != null && delta.syncEpoch != knownEpoch && (smsCheckpoint > 0 || callCheckpoint > 0)) {
                BridgeLog.w(TAG, "Host store changed; restarting sync from zero")
                prefs.setSyncEpoch(delta.syncEpoch)
                prefs.setCheckpoints(0, 0)
                return sync(link)
            }
            delta.sms.forEach { if (messages.applyRemote(it)) threadsWithNewMessages += PhoneNumbers.normalize(it.address) }
            delta.calls.forEach { calls.apply(it) }
            smsCheckpoint = delta.smsCheckpoint
            callCheckpoint = delta.callCheckpoint
            prefs.setSyncEpoch(delta.syncEpoch)
            prefs.setCheckpoints(smsCheckpoint, callCheckpoint)
            if (!delta.hasMore) {
                threadsWithNewMessages.forEach { notifier.notifyThread(it) }
                return
            }
        }
    }

    private companion object {
        const val TAG = "Sync"
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 50
    }
}
