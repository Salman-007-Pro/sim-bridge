package com.simbridge.host.sms

import com.simbridge.core.protocol.Ack
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.SmsSendRequest
import com.simbridge.core.protocol.SyncDelta
import com.simbridge.core.protocol.SyncRequest
import com.simbridge.core.protocol.decodeBody
import com.simbridge.core.transport.BridgeSession
import com.simbridge.host.data.HostDatabase
import com.simbridge.host.data.HostPrefs
import com.simbridge.host.session.HostCommandHandler
import javax.inject.Inject
import javax.inject.Singleton

/** SMS_SEND_REQUEST (idempotent by clientMessageId) and checkpoint-based SYNC_REQUEST. */
@Singleton
class SmsCommandHandler @Inject constructor(
    private val gateway: SmsGateway,
    private val repository: HostSmsRepository,
    private val db: HostDatabase,
    private val prefs: HostPrefs,
) : HostCommandHandler {
    override val types = setOf(MessageType.SMS_SEND_REQUEST, MessageType.SYNC_REQUEST)

    override suspend fun handle(session: BridgeSession, envelope: Envelope) {
        when (envelope.type) {
            MessageType.SMS_SEND_REQUEST -> send(session, envelope)
            MessageType.SYNC_REQUEST -> sync(session, envelope)
            else -> Unit
        }
    }

    override suspend fun onWipe() {
        repository.clear()
        db.calls().clear()
        prefs.resetSyncEpoch()
    }

    private suspend fun send(session: BridgeSession, envelope: Envelope) {
        val request = envelope.decodeBody(SmsSendRequest.serializer())
        when (val result = gateway.send(request.clientMessageId, request.to, request.text)) {
            is SmsSendResult.Accepted ->
                session.send(MessageType.ACK, Ack.serializer(), Ack(result.sms.id.toString()), correlationId = envelope.messageId)
            is SmsSendResult.Duplicate -> {
                session.send(MessageType.ACK, Ack.serializer(), Ack(result.sms.id.toString()), correlationId = envelope.messageId)
                gateway.publish(result.sms)
            }
            is SmsSendResult.Refused -> session.send(
                MessageType.ERROR,
                ErrorBody.serializer(),
                ErrorBody(result.code, result.message),
                correlationId = envelope.messageId,
            )
        }
    }

    private suspend fun sync(session: BridgeSession, envelope: Envelope) {
        val request = envelope.decodeBody(SyncRequest.serializer())
        val limit = request.limit.coerceIn(1, MAX_PAGE)
        val sms = repository.changesSince(request.smsCheckpoint, limit)
        val calls = db.calls().changesSince(request.callCheckpoint, limit)
        val delta = SyncDelta(
            syncEpoch = prefs.syncEpoch(),
            sms = sms.map { it.toProtocol() },
            calls = calls.map { it.toProtocol() },
            smsCheckpoint = sms.lastOrNull()?.changeSeq ?: request.smsCheckpoint,
            callCheckpoint = calls.lastOrNull()?.changeSeq ?: request.callCheckpoint,
            hasMore = sms.size == limit || calls.size == limit,
        )
        session.send(MessageType.SYNC_DELTA, SyncDelta.serializer(), delta, correlationId = envelope.messageId)
    }

    private companion object {
        const val MAX_PAGE = 300
    }
}
