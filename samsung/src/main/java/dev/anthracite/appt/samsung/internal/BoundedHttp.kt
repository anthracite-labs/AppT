package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvFailure
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The single, bounded device-info read over port 8001 (docs/architecture/protocol.md#endpoints).
 *
 * One `GET /api/v2/` to the candidate's own address, bound to the scan's network. It sends no token
 * and no body, does not follow redirects (anything other than `200` is unreadable), reads at most
 * [MAX_DEVICE_INFO_BYTES] of body, and never logs the URL or the document.
 *
 * Why a socket and not an HTTP library: device-info on 8001 is plaintext, and Android's cleartext
 * policy (enforced by HTTP libraries on API 28+) would require an application-wide cleartext
 * network-security configuration for this one request. That is a security-posture decision the
 * architecture has not taken; discovery.md already lists raw sockets among the V1 probes. See the
 * S02 pull request for the follow-up question this raises for the S03 session.
 */
internal class DeviceInfoHttp(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
) {
    suspend fun get(lan: Lan, address: InetAddress, port: Int): String? {
        val socket = Socket()
        return try {
            socket.use {
                blockingIo(dispatcher, onCancel = socket::close) {
                    lan.bind(socket)
                    socket.soTimeout = readTimeoutMillis
                    socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                    writeRequest(socket.outputStream, "${address.hostAddress}:$port")
                    BoundedHttpResponse.readOkBody(socket.inputStream, MAX_DEVICE_INFO_BYTES)
                }
            }
        } catch (denied: SecurityException) {
            throw ScanAbort(LanPolicy.failureFor(denied), denied)
        } catch (failed: IOException) {
            // A blocked local-network socket ends the scan once; anything else just means this
            // candidate's device-info is unreadable, so it gets no card.
            val failure = LanPolicy.failureFor(failed)
            if (failure == TvFailure.LocalNetworkDenied) throw ScanAbort(failure, failed)
            null
        }
    }

    private fun writeRequest(output: OutputStream, hostHeader: String) {
        val request =
            "GET $DEVICE_INFO_PATH HTTP/1.1\r\n" +
                "Host: $hostHeader\r\n" +
                "Accept: application/json\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        output.write(request.encodeToByteArray())
        output.flush()
    }

    companion object {
        const val DEVICE_INFO_PATH: String = "/api/v2/"

        /** protocol.md: connect timeout 5 seconds. */
        const val CONNECT_TIMEOUT_MILLIS: Int = 5_000
        const val READ_TIMEOUT_MILLIS: Int = 3_000
    }
}

/**
 * A minimal, bounded HTTP/1.1 response reader: status line, headers, then a body delimited by
 * `Content-Length`, chunked transfer coding, or end of stream. Every read is capped; over-limit or
 * malformed input returns null rather than growing a buffer.
 */
internal object BoundedHttpResponse {
    private const val MAX_LINE_BYTES = 8 * 1024
    private const val MAX_HEADER_LINES = 64
    private const val HEX_RADIX = 16
    private val OK_STATUS = Regex("^HTTP/1\\.[01] 200(\\s.*)?$")

    fun readOkBody(input: InputStream, maxBodyBytes: Int): String? {
        val buffered = input.buffered()
        val status = readLine(buffered)
        val headers =
            if (status != null && OK_STATUS.matches(status)) readHeaders(buffered) else null
        return headers?.let { readBody(buffered, it, maxBodyBytes) }?.decodeToString()
    }

    private fun readBody(
        input: InputStream,
        headers: Map<String, String>,
        maxBodyBytes: Int,
    ): ByteArray? {
        val contentLength = headers["CONTENT-LENGTH"]
        return when {
            headers["TRANSFER-ENCODING"]?.lowercase(Locale.ROOT)?.contains("chunked") == true ->
                readChunked(input, maxBodyBytes)
            contentLength != null ->
                contentLength.toIntOrNull()?.let { readExactly(input, it, maxBodyBytes) }
            else -> readToEnd(input, maxBodyBytes)
        }
    }

    private fun readHeaders(input: InputStream): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        repeat(MAX_HEADER_LINES) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) return headers
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().uppercase(Locale.ROOT)] =
                    line.substring(colon + 1).trim()
            }
        }
        return null
    }

    private fun readChunked(input: InputStream, maxBodyBytes: Int): ByteArray? {
        val body = ByteArrayOutputStream()
        var complete = false
        var valid = true
        while (valid && !complete) {
            val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(HEX_RADIX)
            when {
                size == null || size < 0 || body.size() + size > maxBodyBytes -> valid = false
                size == 0 -> complete = true
                else -> {
                    val chunk = readExactly(input, size, maxBodyBytes)
                    chunk?.let(body::write)
                    valid = chunk != null && readLine(input) == ""
                }
            }
        }
        return if (complete) body.toByteArray() else null
    }

    private fun readExactly(input: InputStream, length: Int, maxBodyBytes: Int): ByteArray? {
        if (length < 0 || length > maxBodyBytes) return null
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    private fun readToEnd(input: InputStream, maxBodyBytes: Int): ByteArray? {
        val body = ByteArrayOutputStream()
        val buffer = ByteArray(MAX_LINE_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return body.toByteArray()
            if (body.size() + read > maxBodyBytes) return null
            body.write(buffer, 0, read)
        }
    }

    /**
     * One CRLF- or LF-terminated line, without the terminator; null at end of stream or over-limit.
     */
    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (line.size() <= MAX_LINE_BYTES) {
            when (val byte = input.read()) {
                -1 -> return null
                '\n'.code -> return line.toByteArray().decodeToString().removeSuffix("\r")
                else -> line.write(byte)
            }
        }
        return null
    }
}
