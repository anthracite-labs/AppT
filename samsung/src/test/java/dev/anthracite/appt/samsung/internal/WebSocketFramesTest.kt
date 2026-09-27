package dev.anthracite.appt.samsung.internal

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RFC 6455 encode/decode bounds for the plaintext remote channel. */
class WebSocketFramesTest {
    @Test
    fun encodeMaskedTextUsesFinMaskAndThePayloadXor() {
        val mask = byteArrayOf(1, 2, 3, 4)
        val encoded =
            WebSocketFrames.encodeMasked(WebSocketFrames.OPCODE_TEXT, "hi".toByteArray(), mask)

        assertEquals(0x81.toByte(), encoded[0])
        assertEquals((0x80 or 2).toByte(), encoded[1])
        assertEquals(1.toByte(), encoded[2])
        assertEquals(2.toByte(), encoded[3])
        assertEquals(3.toByte(), encoded[4])
        assertEquals(4.toByte(), encoded[5])
        assertEquals(('h'.code xor 1).toByte(), encoded[6])
        assertEquals(('i'.code xor 2).toByte(), encoded[7])
    }

    @Test
    fun readAcceptsAnUnmaskedServerTextFrame() {
        val inbound =
            WebSocketFrames.read(ByteArrayInputStream(byteArrayOf(0x81.toByte(), 0x02, 0x68, 0x69)))

        val frame = inbound as WebSocketFrames.Inbound.Frame
        assertTrue(frame.fin)
        assertEquals(WebSocketFrames.OPCODE_TEXT, frame.opcode)
        assertEquals("hi", frame.payload.decodeToString())
    }

    @Test
    fun readTreatsAMaskedOrRsvOrOversizeFrameAsInvalid() {
        val masked = byteArrayOf(0x81.toByte(), (0x80 or 0).toByte())
        assertEquals(
            WebSocketFrames.Inbound.Invalid,
            WebSocketFrames.read(ByteArrayInputStream(masked)),
        )

        val rsv = byteArrayOf(0xC1.toByte(), 0x00)
        assertEquals(
            WebSocketFrames.Inbound.Invalid,
            WebSocketFrames.read(ByteArrayInputStream(rsv)),
        )

        val oversizeHeader = byteArrayOf(0x81.toByte(), 127, 0, 0, 0, 0, 0, 1, 0, 1)
        assertEquals(
            WebSocketFrames.Inbound.Invalid,
            WebSocketFrames.read(ByteArrayInputStream(oversizeHeader)),
        )
    }

    @Test
    fun readTreatsEndOfStreamAsEnd() {
        assertEquals(
            WebSocketFrames.Inbound.End,
            WebSocketFrames.read(ByteArrayInputStream(ByteArray(0))),
        )
    }

    @Test
    fun readTreatsThrownIoExceptionAsEnd() {
        val throwing =
            object : InputStream() {
                override fun read(): Int = throw IOException("connection reset")
            }
        assertEquals(WebSocketFrames.Inbound.End, WebSocketFrames.read(throwing))

        val afterHeader =
            object : InputStream() {
                private var reads = 0

                override fun read(): Int {
                    reads += 1
                    if (reads == 1) return 0x81
                    throw IOException("reset after header")
                }
            }
        assertEquals(WebSocketFrames.Inbound.End, WebSocketFrames.read(afterHeader))
    }
}
