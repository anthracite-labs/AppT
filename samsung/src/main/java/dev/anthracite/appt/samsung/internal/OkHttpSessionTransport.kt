package dev.anthracite.appt.samsung.internal

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The production [SessionTransport] adapter: the repository-adopted OkHttp WebSocket and TLS stack
 * (docs/architecture/modules.md#internal-seams-inside-samsung, protocol.md#tls).
 *
 * One client per session, so one television's candidate pin can never be confused with another's.
 * There is no body or header logging interceptor, and no URL, frame, token, certificate, address or
 * port is ever logged from here (protocol.md#logging-from-this-layer).
 */
internal class OkHttpSessionTransport : SessionTransport {

    private val trustManager = SpkiTrustManager()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .pingInterval(KEEPALIVE_INTERVAL)
            .sslSocketFactory(sslSocketFactory(trustManager), trustManager)
            .hostnameVerifier(CheckedCertificateVerifier(trustManager))
            .build()
    }

    override suspend fun connect(television: ConfirmedTelevision): SessionConnection? {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Boolean>()
        val socket = openSocket(television, inbound, opened)
        if (socket == null) {
            inbound.close()
            return null
        }
        val connected =
            try {
                opened.await()
            } catch (cancellation: CancellationException) {
                socket.cancel()
                throw cancellation
            }
        if (!connected) {
            socket.cancel()
            return null
        }
        return OkHttpSessionConnection(socket, inbound, trustManager)
    }

    private fun openSocket(
        television: ConfirmedTelevision,
        inbound: Channel<String>,
        opened: CompletableDeferred<Boolean>,
    ): WebSocket? {
        trustManager.beginHandshake()
        val listener =
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    inbound.trySend(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.complete(false)
                    inbound.close()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    inbound.close()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    opened.complete(false)
                    inbound.close()
                }
            }
        val request = Request.Builder().url(RemoteChannel.remoteUrl(television)).build()
        return try {
            client.newWebSocket(request, listener)
        } catch (unusable: IllegalArgumentException) {
            // An unusable URL never reaches a socket. Contained here, not across the seam.
            opened.complete(false)
            null
        }
    }

    private class OkHttpSessionConnection(
        private val socket: WebSocket,
        private val inbound: Channel<String>,
        private val trustManager: SpkiTrustManager,
    ) : SessionConnection {

        override val frames: Flow<String> = flow {
            for (frame in inbound) {
                emit(frame)
            }
        }

        override val certificateIdentity: String?
            get() = trustManager.candidate()

        override suspend fun send(frame: String): Boolean =
            socket.queueSize() < UNSENT_FRAME_CAP && socket.send(frame)

        override fun close() {
            socket.cancel()
            inbound.close()
        }
    }

    companion object {
        /** protocol.md: connect timeout. */
        val CONNECT_TIMEOUT: Duration = 5.seconds

        /** protocol.md: keepalive ping interval on the live socket. */
        val KEEPALIVE_INTERVAL: Duration = 20.seconds

        /** samsung-interface.md: unsent frames held before further commands are rejected. */
        const val UNSENT_FRAME_CAP: Long = 32L
    }
}

/**
 * The identity check for the television's certificate (protocol.md#tls, connection.md).
 *
 * The television presents a self-signed certificate, so the system trust store cannot be the
 * decision: the SPKI is. On first contact there is no saved pin, so the module accepts one
 * certificate as a candidate to speak the handshake and keeps it in memory for that session only. A
 * second, different certificate on the same connection is rejected. Persisting the candidate together
 * with the token, and comparing a saved pin before the token is placed on the wire, is S04.
 */
internal class SpkiTrustManager : X509TrustManager {

    private val candidatePin = AtomicReference<String?>(null)

    /** Clears the per-connection candidate, so one socket's pin is never read as another's. */
    fun beginHandshake() {
        candidatePin.set(null)
    }

    fun candidate(): String? = candidatePin.get()

    fun hasCheckedCertificate(): Boolean = candidatePin.get() != null

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificates = chain ?: throw IllegalArgumentException("no peer certificate chain")
        // A candidate pin is the identity decision, not a licence to accept an expired certificate.
        certificates.forEach { certificate -> certificate.checkValidity() }
        val pin = spkiSha256(certificates.first())
        val previous = candidatePin.getAndSet(pin)
        if (previous != null && previous != pin) {
            throw CertificateException("the television presented a second certificate")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        /** SHA-256 of the certificate SubjectPublicKeyInfo, lowercase hex. */
        fun spkiSha256(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256")
                .digest(certificate.publicKey.encoded)
                .joinToString("") { byte -> String.format("%02x", byte) }
    }
}

/**
 * protocol.md#tls: "Hostname mismatch against the IP is ignored only after the pin check passes."
 *
 * The television is reached by address, so its certificate does not carry that address as a hostname
 * and the platform verifier cannot succeed. The SPKI check in [SpkiTrustManager] is the identity
 * decision, and it runs during the TLS handshake before this verifier is consulted. This verifier
 * therefore requires that the handshake recorded a checked certificate, and otherwise defers to the
 * platform verifier rather than accepting anything.
 */
private class CheckedCertificateVerifier(private val trustManager: SpkiTrustManager) :
    HostnameVerifier {

    private val platform: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(hostname: String?, session: SSLSession?): Boolean =
        trustManager.hasCheckedCertificate() ||
            (hostname != null && session != null && platform.verify(hostname, session))
}

private fun sslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(trustManager), null)
    return context.socketFactory
}
