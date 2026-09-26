package dev.anthracite.appt.samsung.internal

import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom

/**
 * RFC 6455 client frames for the plaintext remote channel. Masked outbound, bounded inbound,
 * nothing logged. Over-limit or malformed input is [Inbound.Invalid], never a thrown parser
 * exception (docs/architecture/protocol.md#parser-limits).
 */
internal object WebSocketFrames {
    /** protocol.md: one frame. */
    const val MAX_FRAME_BYTES: Int = 64 * 1024

    const val OPCODE_CONTINUATION: Int = 0x0
    const val OPCODE_TEXT: Int = 0x1
    const val OPCODE_CLOSE: Int = 0x8
    const val OPCODE_PING: Int = 0x9
    const val OPCODE_PONG: Int = 0xA

    private const val FIN_BIT = 0x80
    private const val MASK_BIT = 0x80
    private const val OPCODE_MASK = 0x0F
    private const val LENGTH_MASK = 0x7F
    private const val LENGTH_7_MAX = 125
    private const val LENGTH_16_MARKER = 126
    private const val LENGTH_64_MARKER = 127
    private const val LENGTH_16_MAX = 0xFFFF
    private const val MASK_KEY_BYTES = 4
    private const val HEADER_BYTES = 2
    private const val LENGTH_16_BYTES = 2
    private const val LENGTH_64_BYTES = 8
    private const val RSV_MASK = 0x70
    private const val BITS_PER_BYTE = 8

    private val random = SecureRandom()

    sealed class Inbound {
        data class Frame(val opcode: Int, val payload: ByteArray, val fin: Boolean) : Inbound()

        data object End : Inbound()

        data object Invalid : Inbound()
    }

    fun randomMask(): ByteArray = ByteArray(MASK_KEY_BYTES).also { random.nextBytes(it) }

    fun encodeMasked(
        opcode: Int,
        payload: ByteArray,
        maskKey: ByteArray = randomMask(),
    ): ByteArray {
        val length = payload.size
        val extra = extraLengthBytes(length)
        val header = HEADER_BYTES + extra + MASK_KEY_BYTES
        val out = ByteArray(header + length)
        out[0] = (FIN_BIT or opcode).toByte()
        writeMaskedLength(out, length, extra)
        maskKey.copyInto(out, HEADER_BYTES + extra)
        payload.forEachIndexed { index, byte ->
            val mask = maskKey[index % MASK_KEY_BYTES].toInt()
            out[header + index] = (byte.toInt() xor mask).toByte()
        }
        return out
    }

    fun encodeText(text: String): ByteArray =
        encodeMasked(OPCODE_TEXT, text.toByteArray(Charsets.UTF_8))

    fun encodePing(): ByteArray = encodeMasked(OPCODE_PING, ByteArray(0))

    fun encodePong(payload: ByteArray): ByteArray = encodeMasked(OPCODE_PONG, payload)

    fun encodeClose(): ByteArray = encodeMasked(OPCODE_CLOSE, ByteArray(0))

    fun read(input: InputStream): Inbound {
        return try {
            val first = input.read()
            val second = if (first < 0) -1 else input.read()
            if (first < 0 || second < 0) Inbound.End else readBody(input, first, second)
        } catch (_: IOException) {
            // Header, length, and payload reads all fail this way on a reset socket.
            Inbound.End
        }
    }

    private fun readBody(input: InputStream, first: Int, second: Int): Inbound {
        val length = readLength(input, second and LENGTH_MASK)
        val invalid = first and RSV_MASK != 0 || second and MASK_BIT != 0 || length == null
        val payload = length.takeUnless { invalid }?.let { readExactly(input, it) }
        return when {
            invalid -> Inbound.Invalid
            payload == null -> Inbound.End
            else -> Inbound.Frame(first and OPCODE_MASK, payload, first and FIN_BIT != 0)
        }
    }

    private fun extraLengthBytes(length: Int): Int =
        when {
            length <= LENGTH_7_MAX -> 0
            length <= LENGTH_16_MAX -> LENGTH_16_BYTES
            else -> LENGTH_64_BYTES
        }

    private fun writeMaskedLength(out: ByteArray, length: Int, extra: Int) {
        when (extra) {
            0 -> out[1] = (MASK_BIT or length).toByte()
            LENGTH_16_BYTES -> {
                out[1] = (MASK_BIT or LENGTH_16_MARKER).toByte()
                out[2] = (length ushr BITS_PER_BYTE).toByte()
                out[3] = length.toByte()
            }
            else -> {
                out[1] = (MASK_BIT or LENGTH_64_MARKER).toByte()
                writeLongLength(out, length.toLong())
            }
        }
    }

    private fun writeLongLength(out: ByteArray, length: Long) {
        var remaining = length
        for (index in (HEADER_BYTES + LENGTH_64_BYTES - 1) downTo HEADER_BYTES) {
            out[index] = remaining.toByte()
            remaining = remaining ushr BITS_PER_BYTE
        }
    }

    private fun readLength(input: InputStream, sevenBits: Int): Int? =
        when (sevenBits) {
            LENGTH_16_MARKER -> readUnsigned(input, LENGTH_16_BYTES)
            LENGTH_64_MARKER -> readUnsigned(input, LENGTH_64_BYTES)
            else -> if (sevenBits <= MAX_FRAME_BYTES) sevenBits else null
        }

    private fun readUnsigned(input: InputStream, width: Int): Int? {
        var value = 0L
        repeat(width) {
            val byte = input.read()
            if (byte < 0) return null
            value = (value shl BITS_PER_BYTE) or byte.toLong()
        }
        return if (value in 0..MAX_FRAME_BYTES.toLong()) value.toInt() else null
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read =
                try {
                    input.read(bytes, offset, length - offset)
                } catch (_: IOException) {
                    return null
                }
            if (read < 0) return null
            offset += read
        }
        return bytes
    }
}
