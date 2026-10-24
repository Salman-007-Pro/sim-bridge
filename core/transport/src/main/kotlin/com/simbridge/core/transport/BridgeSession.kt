package com.simbridge.core.transport

import com.simbridge.core.protocol.DecodeResult
import com.simbridge.core.protocol.Envelope
import com.simbridge.core.protocol.ErrorBody
import com.simbridge.core.protocol.ErrorCode
import com.simbridge.core.protocol.Heartbeat
import com.simbridge.core.protocol.MessageType
import com.simbridge.core.protocol.ProtocolCodec
import com.simbridge.core.protocol.ProtocolVersion
import com.simbridge.core.protocol.SequenceGuard
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

enum class CloseReason { LOCAL, PEER_CLOSED, TIMEOUT, IO_ERROR, PROTOCOL_ERROR, AUTH_FAILED, REPLACED, REVOKED }

class SessionClosedException(val reason: CloseReason?) : IOException("Session closed: $reason")

data class SessionConfig(
    val heartbeatIntervalMs: Long = 5_000,
    val heartbeatTimeoutMs: Long = 15_000,
    val maxConsecutiveErrors: Int = 5,
    val incomingCapacity: Int = 256,
)

/**
 * One authenticated-encryption channel (TLS 1.3) carrying protocol envelopes.
 * Assigns outgoing sequence numbers, rejects replayed or out-of-session frames, answers
 * malformed input with ERROR, keeps the link alive with heartbeats and routes replies to [request].
 */
class BridgeSession(
    private val socket: Socket,
    val peerFingerprint: String,
    sessionId: String?,
    parentScope: CoroutineScope,
    private val config: SessionConfig = SessionConfig(),
    private val codec: ProtocolCodec = ProtocolCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO,
    )
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val writeLock = Mutex()
    private val sequenceGuard = SequenceGuard()
    private val outgoingSeq = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Envelope>>()
    private val inbox = Channel<Envelope>(config.incomingCapacity)
    private val _closeReason = MutableStateFlow<CloseReason?>(null)

    @Volatile
    private var lastReceivedAtMs = clock()

    @Volatile
    var sessionId: String? = sessionId
        private set

    val closeReason: StateFlow<CloseReason?> = _closeReason
    val isOpen: Boolean get() = _closeReason.value == null
    val remoteAddress: String? = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress

    /** Application messages; heartbeats and replies consumed by [request] are not delivered here. */
    val incoming: Flow<Envelope> = inbox.receiveAsFlow()

    fun start() {
        scope.launch { readLoop() }
        scope.launch { heartbeatLoop() }
    }

    suspend fun receive(timeoutMs: Long): Envelope? =
        withTimeoutOrNull(timeoutMs) { inbox.receiveCatching().getOrNull() }

    suspend fun <T> send(
        type: MessageType,
        serializer: KSerializer<T>,
        body: T,
        correlationId: String? = null,
    ): Envelope = sendRaw(type, ProtocolCodec.encodeBody(type, serializer, body), correlationId, UUID.randomUUID().toString())

    /** Sends a request and waits for the first envelope whose correlationId is the request's messageId. */
    suspend fun <T> request(
        type: MessageType,
        serializer: KSerializer<T>,
        body: T,
        timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    ): Envelope? {
        val messageId = UUID.randomUUID().toString()
        val reply = CompletableDeferred<Envelope>()
        pending[messageId] = reply
        return try {
            sendRaw(type, ProtocolCodec.encodeBody(type, serializer, body), null, messageId)
            withTimeoutOrNull(timeoutMs) { reply.await() }
        } finally {
            pending.remove(messageId)
        }
    }

    suspend fun awaitClosed(): CloseReason = _closeReason.filterNotNull().first()

    fun close(reason: CloseReason = CloseReason.LOCAL) {
        if (!_closeReason.compareAndSet(null, reason)) return
        runCatching { socket.close() }
        inbox.close()
        pending.values.forEach { it.cancel() }
        pending.clear()
        scope.cancel()
    }

    private suspend fun sendRaw(type: MessageType, body: JsonObject, correlationId: String?, messageId: String): Envelope {
        if (!isOpen) throw SessionClosedException(_closeReason.value)
        return writeLock.withLock {
            val envelope = Envelope(
                protocolVersion = ProtocolVersion.CURRENT,
                messageId = messageId,
                sessionId = sessionId ?: UNASSIGNED_SESSION,
                seq = outgoingSeq.incrementAndGet(),
                sentAtEpochMs = clock(),
                type = type,
                correlationId = correlationId,
                body = body,
            )
            try {
                withContext(Dispatchers.IO) { FrameIO.write(output, codec.encode(envelope)) }
            } catch (e: IOException) {
                close(CloseReason.IO_ERROR)
                throw SessionClosedException(CloseReason.IO_ERROR)
            }
            envelope
        }
    }

    private suspend fun readLoop() {
        var consecutiveErrors = 0
        try {
            while (isOpen) {
                val bytes = FrameIO.read(input)
                lastReceivedAtMs = clock()
                val accepted = when (val result = codec.decode(bytes)) {
                    is DecodeResult.Rejected -> {
                        sendError(result.code, result.reason, result.messageId)
                        false
                    }
                    is DecodeResult.Ok -> dispatch(result.envelope)
                }
                consecutiveErrors = if (accepted) 0 else consecutiveErrors + 1
                if (consecutiveErrors >= config.maxConsecutiveErrors) {
                    close(CloseReason.PROTOCOL_ERROR)
                }
            }
        } catch (e: FrameException) {
            close(CloseReason.PROTOCOL_ERROR)
        } catch (e: EOFException) {
            close(CloseReason.PEER_CLOSED)
        } catch (e: IOException) {
            close(CloseReason.IO_ERROR)
        }
    }

    private suspend fun dispatch(envelope: Envelope): Boolean {
        val current = sessionId
        if (current == null) {
            sessionId = envelope.sessionId
        } else if (current != envelope.sessionId) {
            sendError(ErrorCode.SESSION_MISMATCH, "Unknown session", envelope.messageId)
            return false
        }
        if (!sequenceGuard.accept(envelope.seq)) {
            sendError(ErrorCode.REPLAY, "Replayed or reordered frame", envelope.messageId)
            return false
        }
        if (envelope.type == MessageType.HEARTBEAT) return true
        envelope.correlationId?.let { id -> pending.remove(id)?.let { it.complete(envelope); return true } }
        inbox.send(envelope)
        return true
    }

    private suspend fun heartbeatLoop() {
        while (isOpen) {
            delay(config.heartbeatIntervalMs)
            if (clock() - lastReceivedAtMs > config.heartbeatTimeoutMs) {
                close(CloseReason.TIMEOUT)
                return
            }
            if (sessionId != null) {
                runCatching { send(MessageType.HEARTBEAT, Heartbeat.serializer(), Heartbeat(clock())) }
            }
        }
    }

    private suspend fun sendError(code: ErrorCode, reason: String, relatesTo: String?) {
        runCatching {
            send(MessageType.ERROR, ErrorBody.serializer(), ErrorBody(code, reason), relatesTo?.takeIf { it.isNotBlank() })
        }
    }

    companion object {
        const val UNASSIGNED_SESSION = "unassigned"
        const val DEFAULT_REQUEST_TIMEOUT_MS = 10_000L
    }
}
