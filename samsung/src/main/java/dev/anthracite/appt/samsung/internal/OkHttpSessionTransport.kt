package dev.anthracite.appt.samsung.internal

import android.annotation.SuppressLint
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Hexadecimal radix, named because detekt's MagicNumber does not ignore 16. File-private: the two
 * classes here that build hex both build lowercase hex.
 */
private const val HEX_RADIX = 16

/**
 * The TLS [SessionTransport] adapter: OkHttp WebSocket on `wss` port 8002
 * (docs/architecture/modules.md#internal-seams-inside-samsung, protocol.md#tls).
 *
 * Plaintext port 8001 is not opened here. Android's cleartext policy would reject `ws://` on this
 * stack at targetSdk 36; [PlaintextWebSocketTransport] speaks that fallback on a raw socket.
 *
 * The saved-identity discipline (docs/architecture/connection.md#ordering-relative-to-secrets): a
 * saved SPKI pin is verified against a live handshake with this host *before* the token is attached
 * to the remote-channel URL, so a changed television identity is
 * [ConnectionAttempt.IdentityMismatch] with the token absent from every attempted URL and write.
 * Only after that verification does the resumed connection open with its token. On first contact
 * there is no saved pin: one certificate is accepted as an in-memory candidate for the session and
 * is persisted only together with a successful approval.
 *
 * One client and one trust manager per connection attempt, so two concurrent attempts can never
 * read each other's candidate pin, and no URL, frame, token, certificate, address or port is ever
 * logged from here (protocol.md#logging-from-this-layer).
 */
internal class OkHttpSessionTransport(private val ioDispatcher: CoroutineContext = Dispatchers.IO) :
    SessionTransport {

    override suspend fun connect(
        television: ConfirmedTelevision,
        saved: PairingSecret?,
    ): ConnectionAttempt {
        val requiredPin = saved?.pin?.takeIf { television.tls }
        if (requiredPin != null) {
            when (verifySavedPin(television.host, television.remotePort, requiredPin)) {
                IdentityProbe.IDENTITY_CHANGED -> return ConnectionAttempt.IdentityMismatch
                IdentityProbe.FAILED -> return ConnectionAttempt.Unreachable
                IdentityProbe.MATCHED -> Unit
            }
        }
        // The token is only ever attached after the saved pin matched a live handshake with this
        // host, or on first contact when there is no saved pin and no token.
        val token = saved?.token?.takeIf { requiredPin != null }
        return open(television, requiredPin, token)
    }

    /**
     * The saved-pin check (connection.md#security-identity): a real TLS handshake against this
     * host, with a trust manager that accepts nothing but the saved pin. The WebSocket handshake
     * below re-enforces the same pin, so the probe is the fail-closed gate and the session keeps
     * the guarantee for its whole lifetime.
     */
    private suspend fun verifySavedPin(
        host: String,
        port: Int,
        requiredPin: String,
    ): IdentityProbe {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake(requiredPin)
        return try {
            withContext(ioDispatcher) {
                val timeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
                sslSocketFactory(trustManager).createSocket().use { socket ->
                    (socket as SSLSocket).apply {
                        soTimeout = timeoutMillis
                        connect(
                            InetSocketAddress(InetAddress.getByName(unbracketed(host)), port),
                            timeoutMillis,
                        )
                        startHandshake()
                    }
                }
            }
            IdentityProbe.MATCHED
        } catch (ignored: SavedIdentityMismatchException) {
            IdentityProbe.IDENTITY_CHANGED
        } catch (ignored: GeneralSecurityException) {
            // Lapsed certificates and unusable chains are not the saved identity either.
            if (ignored.wasCausedBySavedIdentityMismatch()) {
                IdentityProbe.IDENTITY_CHANGED
            } else {
                IdentityProbe.FAILED
            }
        } catch (ignored: IOException) {
            // Unreachable hosts and handshake transport failures are reachability, not identity.
            // A TLS stack may wrap the trust manager's decision in its own handshake exception,
            // so unwrap the cause chain before classifying: a changed identity is never reported
            // as mere unreachability (connection.md#security-identity).
            if (ignored.wasCausedBySavedIdentityMismatch()) {
                IdentityProbe.IDENTITY_CHANGED
            } else {
                IdentityProbe.FAILED
            }
        }
    }

    /** True when this throwable's cause chain reaches [SavedIdentityMismatchException]. */
    private fun Throwable.wasCausedBySavedIdentityMismatch(): Boolean =
        generateSequence(this as Throwable?) { it.cause }
            .take(CAUSE_CHAIN_LIMIT)
            .any { it is SavedIdentityMismatchException }

    private suspend fun open(
        television: ConfirmedTelevision,
        requiredPin: String?,
        token: String?,
    ): ConnectionAttempt {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake(requiredPin)
        val client =
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .pingInterval(KEEPALIVE_INTERVAL)
                .sslSocketFactory(sslSocketFactory(trustManager), trustManager)
                .hostnameVerifier(CheckedCertificateVerifier(trustManager))
                .build()
        val inbound = Channel<String>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Boolean>()
        val socket = openSocket(client, television, token, inbound, opened)
        if (socket == null) {
            shutdown(client)
            inbound.close()
            return ConnectionAttempt.Unreachable
        }
        val connected =
            try {
                opened.await()
            } catch (cancellation: CancellationException) {
                socket.cancel()
                shutdown(client)
                throw cancellation
            }
        if (!connected) {
            socket.cancel()
            shutdown(client)
            return ConnectionAttempt.Unreachable
        }
        return ConnectionAttempt.Opened(
            OkHttpSessionConnection(socket, inbound, trustManager, client)
        )
    }

    private fun openSocket(
        client: OkHttpClient,
        television: ConfirmedTelevision,
        token: String?,
        inbound: Channel<String>,
        opened: CompletableDeferred<Boolean>,
    ): WebSocket? {
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
        // The URL carries the token only when the caller has already verified the saved pin against
        // a live handshake (see [connect]); first contact attaches none.
        val request = Request.Builder().url(RemoteChannel.remoteUrl(television, token)).build()
        return try {
            client.newWebSocket(request, listener)
        } catch (ignored: IllegalArgumentException) {
            // An unusable URL never reaches a socket. Contained here, not across the seam.
            opened.complete(false)
            null
        }
    }

    private fun shutdown(client: OkHttpClient) {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private class OkHttpSessionConnection(
        private val socket: WebSocket,
        private val inbound: Channel<String>,
        private val trustManager: SpkiTrustManager,
        private val client: OkHttpClient,
    ) : SessionConnection {

        override val frames: Flow<String> = flow {
            for (frame in inbound) {
                emit(frame)
            }
        }

        /** The pin this connection's own handshake judged, candidate or required. */
        override val certificateIdentity: String?
            get() = trustManager.candidate()

        override suspend fun send(frame: String): Boolean =
            socket.queueSize() < UNSENT_FRAME_CAP && socket.send(frame)

        override fun close() {
            socket.cancel()
            inbound.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        /** protocol.md: connect timeout. */
        val CONNECT_TIMEOUT: Duration = 5.seconds

        /** Bound for cause-chain walks; TLS wrappers are shallow, and this ends any cycle. */
        private const val CAUSE_CHAIN_LIMIT = 10

        /** protocol.md: keepalive ping interval on the live socket. */
        val KEEPALIVE_INTERVAL: Duration = 20.seconds

        /** samsung-interface.md: unsent frames held before further commands are rejected. */
        const val UNSENT_FRAME_CAP: Long = 32L

        private fun unbracketed(host: String): String = host.removePrefix("[").removeSuffix("]")
    }
}

/** The outcome of the saved-pin probe. */
private enum class IdentityProbe {
    /** The television presented the saved SPKI. The token may now be attached. */
    MATCHED,

    /** The television presented a different SPKI. Fail closed; no token anywhere. */
    IDENTITY_CHANGED,

    /** The host could not be reached, or the handshake failed for another reason. */
    FAILED,
}

/**
 * Thrown when a handshake presents a security identity that does not match the saved one
 * (docs/architecture/connection.md#security-identity). A dedicated type, so the transport can
 * distinguish the fail-closed identity decision from ordinary reachability failures.
 */
internal class SavedIdentityMismatchException :
    CertificateException("the television's security identity changed")

/**
 * The identity check for the television's certificate (protocol.md#tls, connection.md).
 *
 * The television presents a self-signed certificate, so the system trust store cannot be the
 * decision: the SPKI is. Two modes share one shape:
 *
 * * **Candidate mode** (`beginHandshake()`): first contact has no saved pin, so one certificate is
 *   accepted as a candidate to speak the handshake and kept in memory for that session only. A
 *   second, different certificate on the same connection is rejected. Persisting the candidate
 *   together with the token is the approval path.
 * * **Required mode** (`beginHandshake(savedPin)`): a saved pin exists, so nothing but that pin is
 *   accepted, and anything else throws [SavedIdentityMismatchException] before any token-bearing
 *   byte can be written.
 *
 * This is not a trust-all `TrustManager`, which is why lint's `CustomX509TrustManager` warning does
 * not apply here: connection.md#security-identity names exactly these two shapes and forbids a
 * trust-all switch outright. Expiry is still enforced by [X509Certificate.checkValidity] on every
 * chain, so a lapsed certificate is never a candidate and never matches a saved pin.
 */
@SuppressLint("CustomX509TrustManager")
internal class SpkiTrustManager : X509TrustManager {

    private val candidatePin = AtomicReference<String?>(null)
    private val requiredPin = AtomicReference<String?>(null)

    /** Starts a candidate-mode handshake: no saved pin, one in-memory candidate accepted. */
    fun beginHandshake() {
        beginHandshake(savedPin = null)
    }

    /**
     * Starts a required-mode handshake when [savedPin] is present: only that pin is an acceptable
     * television identity. Null — first contact, or the plaintext channel — is candidate mode.
     */
    fun beginHandshake(savedPin: String?) {
        candidatePin.set(null)
        requiredPin.set(savedPin)
    }

    fun candidate(): String? = candidatePin.get()

    fun hasCheckedCertificate(): Boolean = candidatePin.get() != null

    /**
     * AppT is the TLS client in every session this module opens, so a peer never presents a client
     * certificate to it and this is never reached. It refuses rather than accepting, because a path
     * that must not be taken is not a path to leave open.
     */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("AppT is never a TLS server")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificates = requireNotNull(chain) { "no peer certificate chain" }
        // A pin is the identity decision, not a licence to accept an expired certificate.
        certificates.forEach { certificate -> certificate.checkValidity() }
        val pin = spkiSha256(certificates.first())
        val required = requiredPin.get()
        if (required != null) {
            // The saved identity is the only acceptable one; a different SPKI fails closed before
            // any token-bearing byte can be written to this connection.
            if (pin != required) throw SavedIdentityMismatchException()
        } else {
            val previous = candidatePin.getAndSet(pin)
            if (previous != null && previous != pin) {
                throw CertificateException("the television presented a second certificate")
            }
            return
        }
        candidatePin.set(pin)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        /** SHA-256 of the certificate SubjectPublicKeyInfo, lowercase hex. */
        fun spkiSha256(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded).joinToString(
                ""
            ) { byte ->
                byte.toUByte().toString(HEX_RADIX).padStart(2, '0')
            }
    }
}

/**
 * protocol.md#tls: "Hostname mismatch against the IP is ignored only after the pin check passes."
 *
 * The television is reached by address, so its certificate does not carry that address as a
 * hostname and the platform verifier cannot succeed. The SPKI check in [SpkiTrustManager] is the
 * identity decision, and it runs during the TLS handshake before this verifier is consulted. This
 * verifier therefore requires that the handshake recorded a checked certificate, and otherwise
 * defers to the platform verifier rather than accepting anything.
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
