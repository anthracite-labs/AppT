package dev.anthracite.appt.samsung.internal

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedHttpResponseTest {
    private val max = 64

    private fun read(response: String, maxBody: Int = max) =
        BoundedHttpResponse.readOkBody(ByteArrayInputStream(response.encodeToByteArray()), maxBody)

    @Test
    fun contentLengthBody() {
        assertEquals("{}", read("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}trailing"))
    }

    @Test
    fun chunkedBody() {
        val response =
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + "3\r\n{\"a\r\n" + "4;ext=1\r\n\":1}\r\n" + "0\r\n\r\n"
        assertEquals("{\"a\":1}", read(response))
    }

    @Test
    fun bodyUntilEndOfStream() {
        assertEquals("{\"x\":true}", read("HTTP/1.0 200 OK\n\n{\"x\":true}"))
    }

    @Test
    fun overLimitBodiesAreUnreadable() {
        val big = "x".repeat(max + 1)
        assertNull(read("HTTP/1.1 200 OK\r\nContent-Length: ${big.length}\r\n\r\n$big"))
        assertNull(read("HTTP/1.1 200 OK\r\n\r\n$big"))
        assertNull(read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n41\r\n$big\r\n0\r\n\r\n"))
        assertEquals("x".repeat(max), read("HTTP/1.1 200 OK\r\n\r\n${"x".repeat(max)}"))
    }

    @Test
    fun nonOkResponsesAreUnreadableAndRedirectsAreNotFollowed() {
        assertNull(read("HTTP/1.1 301 Moved Permanently\r\nLocation: elsewhere\r\nContent-Length: 0\r\n\r\n"))
        assertNull(read("HTTP/1.1 404 Not Found\r\nContent-Length: 2\r\n\r\n{}"))
        assertNull(read("HTTP/1.1 2000 OK\r\n\r\n{}"))
        assertNull(read("SSH-2.0-OpenSSH\r\n"))
    }

    @Test
    fun truncatedOrMalformedResponsesAreUnreadable() {
        assertNull(read(""))
        assertNull(read("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n{}"))
        assertNull(read("HTTP/1.1 200 OK\r\nContent-Length: nope\r\n\r\n{}"))
        assertNull(read("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"))
        assertNull(read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\n"))
        assertNull(read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}XX0\r\n\r\n"))
        assertNull(read("HTTP/1.1 200 OK\r\nX: ${"y".repeat(9_000)}\r\n\r\n{}"))
        val manyHeaders = (1..70).joinToString("") { "X-$it: v\r\n" }
        assertNull(read("HTTP/1.1 200 OK\r\n$manyHeaders\r\n{}"))
    }
}
