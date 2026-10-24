package com.simbridge.core.protocol

import kotlinx.serialization.KSerializer

/** The one body schema allowed for each message type. */
object MessageSchemas {
    fun serializerFor(type: MessageType): KSerializer<*> = when (type) {
        MessageType.HELLO -> Hello.serializer()
        MessageType.CAPABILITIES -> Capabilities.serializer()
        MessageType.PAIR_REQUEST -> PairRequest.serializer()
        MessageType.PAIR_CONFIRM -> PairConfirm.serializer()
        MessageType.AUTH_RESULT -> AuthResult.serializer()
        MessageType.HEARTBEAT -> Heartbeat.serializer()
        MessageType.HOST_STATUS -> HostStatus.serializer()
        MessageType.CALL_INCOMING, MessageType.CALL_STATE -> CallInfo.serializer()
        MessageType.CALL_DIAL_REQUEST -> DialRequest.serializer()
        MessageType.CALL_ANSWER_REQUEST,
        MessageType.CALL_REJECT_REQUEST,
        MessageType.CALL_HANGUP_REQUEST -> CallCommand.serializer()
        MessageType.SMS_SEND_REQUEST -> SmsSendRequest.serializer()
        MessageType.SMS_RECEIVED -> SmsMessage.serializer()
        MessageType.SMS_STATUS -> SmsStatusUpdate.serializer()
        MessageType.SYNC_REQUEST -> SyncRequest.serializer()
        MessageType.SYNC_DELTA -> SyncDelta.serializer()
        MessageType.ERROR -> ErrorBody.serializer()
        MessageType.ACK -> Ack.serializer()
    }
}
