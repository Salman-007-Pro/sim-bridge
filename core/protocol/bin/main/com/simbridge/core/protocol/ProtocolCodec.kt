package com.simbridge.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

sealed interface DecodeResult {
    data class Ok(val envelope: Envelope) : DecodeResult
    data class Rejected(val code: ErrorCode, val reason: String, val messageId: String? = null) : DecodeResult
}

/**
 * Strict JSON codec: unknown keys, wrong body schema for the type, unsupported versions and
 * out-of-range identifiers are all rejected without throwing.
 */
class ProtocolCodec(private val supportedVersions: Set<Int> = ProtocolVersion.SUPPORTED) {

    fun encode(envelope: Envelope): ByteArray = json.encodeToString(Envelope.serializer(), envelope).encodeToByteArray()

    fun decode(bytes: ByteArray): DecodeResult {
        val root = try {
            json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        } catch (e: Exception) {
            return DecodeResult.Rejected(ErrorCode.MALFORMED, "Not a JSON object")
        }
        val messageId = (root["messageId"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(MAX_ID_LENGTH)
        val version = (root["protocolVersion"] as? JsonPrimitive)?.intOrNull
            ?: return DecodeResult.Rejected(ErrorCode.MALFORMED, "Missing protocolVersion", messageId)
        if (version !in supportedVersions) {
            return DecodeResult.Rejected(ErrorCode.UNSUPPORTED_VERSION, "Unsupported protocol version $version", messageId)
        }
        val envelope = try {
            json.decodeFromJsonElement(Envelope.serializer(), root)
        } catch (e: SerializationException) {
            return DecodeResult.Rejected(ErrorCode.MALFORMED, "Invalid envelope", messageId)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Rejected(ErrorCode.MALFORMED, "Invalid envelope", messageId)
        }
        validateIds(envelope)?.let { return DecodeResult.Rejected(ErrorCode.MALFORMED, it, messageId) }
        return try {
            json.decodeFromJsonElement(MessageSchemas.serializerFor(envelope.type), envelope.body)
            DecodeResult.Ok(envelope)
        } catch (e: SerializationException) {
            DecodeResult.Rejected(ErrorCode.MALFORMED, "Body does not match ${envelope.type}", messageId)
        } catch (e: IllegalArgumentException) {
            DecodeResult.Rejected(ErrorCode.MALFORMED, "Body does not match ${envelope.type}", messageId)
        }
    }

    private fun validateIds(e: Envelope): String? = when {
        e.messageId.isBlank() || e.messageId.length > MAX_ID_LENGTH -> "Invalid messageId"
        e.sessionId.isBlank() || e.sessionId.length > MAX_ID_LENGTH -> "Invalid sessionId"
        e.correlationId != null && (e.correlationId.isBlank() || e.correlationId.length > MAX_ID_LENGTH) -> "Invalid correlationId"
        e.seq < 1 -> "Invalid seq"
        e.sentAtEpochMs < 0 -> "Invalid timestamp"
        else -> null
    }

    companion object {
        const val MAX_ID_LENGTH = 64

        val json: Json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
            isLenient = false
            allowSpecialFloatingPointValues = false
        }

        fun <T> encodeBody(type: MessageType, serializer: KSerializer<T>, body: T): JsonObject {
            require(MessageSchemas.serializerFor(type).descriptor == serializer.descriptor) {
                "Body ${serializer.descriptor.serialName} is not valid for $type"
            }
            return json.encodeToJsonElement(serializer, body).jsonObject
        }
    }
}

fun <T> Envelope.decodeBody(serializer: KSerializer<T>): T =
    ProtocolCodec.json.decodeFromJsonElement(serializer, body)
