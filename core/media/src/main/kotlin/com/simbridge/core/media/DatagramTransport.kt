package com.simbridge.core.media

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One end of the media datagram link. Abstracted so the engine can be tested without a real socket. */
interface DatagramTransport {
    suspend fun send(bytes: ByteArray)

    /** Blocks until a datagram arrives or the poll window elapses; null on timeout. */
    suspend fun receive(): ByteArray?

    val localPort: Int

    fun close()
}

/** Real UDP transport, connected to one remote peer. Works on both the JVM and Android. */
class UdpDatagramTransport private constructor(
    private val socket: DatagramSocket,
    private val maxPayload: Int,
) : DatagramTransport {
    override val localPort: Int get() = socket.localPort

    fun connect(remoteAddress: String, remotePort: Int) {
        socket.connect(InetSocketAddress(InetAddress.getByName(remoteAddress), remotePort))
    }

    override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching { socket.send(DatagramPacket(bytes, bytes.size)) }
        Unit
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        val packet = DatagramPacket(ByteArray(maxPayload), maxPayload)
        try {
            socket.receive(packet)
            packet.data.copyOf(packet.length)
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun close() = runCatching { socket.close() }.let { }

    companion object {
        const val MAX_PAYLOAD = 1_500

        fun bind(preferredPort: Int = 0, pollTimeoutMs: Int = 500, maxPayload: Int = MAX_PAYLOAD): UdpDatagramTransport {
            val socket = DatagramSocket(preferredPort).apply { soTimeout = pollTimeoutMs }
            return UdpDatagramTransport(socket, maxPayload)
        }
    }
}
