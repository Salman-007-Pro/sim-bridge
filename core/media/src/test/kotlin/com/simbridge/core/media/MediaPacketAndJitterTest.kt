package com.simbridge.core.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPacketAndJitterTest {
    @Test
    fun packetRoundTripsHeaderAndPayload() {
        val packet = MediaPacket(seq = 0x01020304, timestampMs = 60, sealed = byteArrayOf(9, 8, 7))
        val decoded = MediaPacket.decode(packet.encode())!!
        assertEquals(packet, decoded)
        assertEquals(0x01020304, decoded.seq)
        assertEquals(60, decoded.timestampMs)
        assertArrayEquals(byteArrayOf(9, 8, 7), decoded.sealed)
    }

    @Test
    fun rejectsShortOrWrongVersionPackets() {
        assertNull(MediaPacket.decode(ByteArray(4)))
        val good = MediaPacket(1, 1, byteArrayOf(1)).encode()
        good[0] = 99
        assertNull(MediaPacket.decode(good))
    }

    @Test
    fun jitterWaitsForTargetDepthThenPlaysInOrder() {
        val jitter = JitterBuffer(targetDepth = 3, capacity = 20)
        assertTrue(jitter.poll() is PlayoutResult.Waiting)
        jitter.offer(0, bytes(0))
        jitter.offer(1, bytes(1))
        assertTrue("below target depth", jitter.poll() is PlayoutResult.Waiting)
        jitter.offer(2, bytes(2))
        assertEquals(0, seq(jitter.poll()))
        assertEquals(1, seq(jitter.poll()))
        assertEquals(2, seq(jitter.poll()))
    }

    @Test
    fun reordersOutOfOrderArrivals() {
        val jitter = JitterBuffer(targetDepth = 1, capacity = 20)
        jitter.offer(2, bytes(2))
        jitter.offer(0, bytes(0))
        jitter.offer(1, bytes(1))
        assertEquals(0, seq(jitter.poll()))
        assertEquals(1, seq(jitter.poll()))
        assertEquals(2, seq(jitter.poll()))
    }

    @Test
    fun dropsLatePacketsAndCountsThem() {
        val jitter = JitterBuffer(targetDepth = 1, capacity = 20)
        jitter.offer(5, bytes(5))
        assertEquals(5, seq(jitter.poll()))
        assertFalse(jitter.offer(4, bytes(4)))
        assertEquals(1, jitter.lateDropped)
    }

    @Test
    fun concealsAMidStreamGapOnceLaterPacketsPileUp() {
        val jitter = JitterBuffer(targetDepth = 2, capacity = 20)
        jitter.offer(0, bytes(0))
        jitter.offer(1, bytes(1))
        assertEquals(0, seq(jitter.poll()))
        assertEquals(1, seq(jitter.poll()))
        // seq 2 is lost; 3 and 4 arrive, so the gap at 2 is concealed rather than stalling forever.
        jitter.offer(3, bytes(3))
        jitter.offer(4, bytes(4))
        assertTrue(jitter.poll() is PlayoutResult.Gap)
        assertEquals(3, seq(jitter.poll()))
        assertEquals(4, seq(jitter.poll()))
        assertEquals(1, jitter.concealed)
    }

    @Test
    fun startsAtFirstArrivalWhenEarlyPacketsNeverCome() {
        val jitter = JitterBuffer(targetDepth = 2, capacity = 20)
        // 0 never arrives; the stream simply begins at the first packet it actually has.
        jitter.offer(1, bytes(1))
        jitter.offer(2, bytes(2))
        assertEquals(1, seq(jitter.poll()))
        assertEquals(2, seq(jitter.poll()))
        assertEquals(0, jitter.concealed)
    }

    private fun bytes(v: Int) = byteArrayOf(v.toByte())
    private fun seq(result: PlayoutResult) = (result as PlayoutResult.Frame).seq
}
