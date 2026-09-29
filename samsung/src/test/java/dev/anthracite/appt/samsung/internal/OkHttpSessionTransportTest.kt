package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** connection.md#security-identity and the containment contract of [SessionTransport.connect]. */
class OkHttpSessionTransportTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val loopbackHost: String = requireNotNull(loopback.hostAddress)

    // connection.md#security-identity: "The module may accept one certificate as a candidate to
    // speak the handshake. The candidate stays in memory."
    @Test
    fun theFirstCertificateIsAcceptedAsACandidateAndItsPinIsTheSpkiSha256() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()

        trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_ONE)), AUTH_TYPE)

        // The pin is asserted against a digest computed from the same DER with `openssl x509
        // -pubkey | openssl pkey -pubin -outform DER | sha256sum`, so it pins the documented shape
        // (SHA-256 of the SubjectPublicKeyInfo, lowercase hex) instead of echoing this code.
        assertEquals(TELEVISION_ONE_PIN, trustManager.candidate())
        assertTrue(trustManager.hasCheckedCertificate())
    }

    // One socket presents one certificate; a re-handshake on it is not a second certificate.
    @Test
    fun theSameCertificateTwiceOnOneConnectionStaysAccepted() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()
        val chain = arrayOf(certificate(TELEVISION_ONE))

        trustManager.checkServerTrusted(chain, AUTH_TYPE)
        trustManager.checkServerTrusted(chain, AUTH_TYPE)

        assertEquals(TELEVISION_ONE_PIN, trustManager.candidate())
    }

    // connection.md#security-identity: "A second, different certificate on that connection is
    // rejected." This is the property that keeps the trust manager from being a trust-all one.
    @Test
    fun aSecondDifferentCertificateOnTheSameConnectionIsRejected() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()
        trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_ONE)), AUTH_TYPE)

        val rejected =
            assertThrows(CertificateException::class.java) {
                trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_TWO)), AUTH_TYPE)
            }

        assertEquals("the television presented a second certificate", rejected.message)
    }

    // connection.md: leaving the pairing wait by close discards the candidate pin. The candidate is
    // per socket, so a later socket starts with no pin and may accept a different television.
    @Test
    fun beginHandshakeDiscardsTheCandidateSoOneSocketsPinIsNeverAnothers() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()
        trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_ONE)), AUTH_TYPE)
        assertTrue(trustManager.hasCheckedCertificate())

        trustManager.beginHandshake()

        assertNull(trustManager.candidate())
        assertFalse(trustManager.hasCheckedCertificate())
        trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_TWO)), AUTH_TYPE)
        assertEquals(TELEVISION_TWO_PIN, trustManager.candidate())
    }

    // Expiry is enforced on every chain, so a lapsed certificate is not a candidate even though
    // there is no saved pin to compare it against.
    @Test
    fun aLapsedCertificateIsNeverACandidate() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()

        assertThrows(CertificateExpiredException::class.java) {
            trustManager.checkServerTrusted(arrayOf(certificate(LAPSED_TELEVISION)), AUTH_TYPE)
        }

        assertNull(trustManager.candidate())
    }

    @Test
    fun aMissingChainIsRejectedAndRecordsNothing() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake()

        assertThrows(IllegalArgumentException::class.java) {
            trustManager.checkServerTrusted(null, AUTH_TYPE)
        }

        assertFalse(trustManager.hasCheckedCertificate())
    }

    // connection.md#security-identity forbids a trust-all switch: AppT is the TLS client in every
    // session it opens, and it advertises no issuer it would accept.
    @Test
    fun theTrustManagerIsNeverAServerAndAcceptsNoIssuers() {
        val trustManager = SpkiTrustManager()

        assertThrows(CertificateException::class.java) {
            trustManager.checkClientTrusted(arrayOf(certificate(TELEVISION_ONE)), AUTH_TYPE)
        }

        assertEquals(0, trustManager.getAcceptedIssuers().size)
    }

    // Required mode: a saved pin admits only that identity, and nothing about a mismatch is
    // recorded — this is the check that runs before any token-bearing byte can be written.
    @Test
    fun requiredModeAcceptsOnlyTheSavedIdentity() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake(TELEVISION_ONE_PIN)

        trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_ONE)), AUTH_TYPE)

        assertEquals(TELEVISION_ONE_PIN, trustManager.candidate())
    }

    @Test
    fun requiredModeRejectsADifferentIdentityBeforeAnythingIsRecorded() {
        val trustManager = SpkiTrustManager()
        trustManager.beginHandshake(TELEVISION_ONE_PIN)

        assertThrows(SavedIdentityMismatchException::class.java) {
            trustManager.checkServerTrusted(arrayOf(certificate(TELEVISION_TWO)), AUTH_TYPE)
        }

        assertFalse(trustManager.hasCheckedCertificate())
        assertNull(trustManager.candidate())
    }

    // SessionTransport.connect: "Returns null when the socket cannot be opened at all...
    // Containment is the adapter's job: nothing about a socket, TLS or parser failure crosses the
    // seam as a thrown exception." The peer owns the adopted TLS port on loopback and drops every
    // connection before a TLS handshake, which drives the real OkHttp stack down its failure path.
    @Test
    fun aTelevisionThatDropsTheTlsHandshakeOpensNoSessionAndNothingCrossesTheSeam() = runBlocking {
        ServerSocket(ConfirmedTelevision.TLS_REMOTE_PORT, 1, loopback).use { server ->
            val peer = thread {
                while (true) {
                    val accepted = runCatching { server.accept() }.getOrNull() ?: break
                    accepted.close()
                }
            }
            val television =
                ConfirmedTelevision(TvId("tv"), loopbackHost, tls = true, adoptedChannel = true)
            val transport = OkHttpSessionTransport()

            val attempt = withTimeout(CONNECT_BOUND) { transport.connect(television, saved = null) }

            server.close()
            peer.join()
            assertEquals(ConnectionAttempt.Unreachable, attempt)
        }
    }

    private fun certificate(derBase64: String): X509Certificate {
        val stream = ByteArrayInputStream(Base64.getDecoder().decode(derBase64))
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(stream) as X509Certificate
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val AUTH_TYPE = "EC"
        val CONNECT_BOUND = 15.seconds

        // Three self-signed loopback certificates, generated for this file only and never used on a
        // network:
        //   openssl req -new -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -keyout /dev/null
        //   openssl ca -notext -startdate 20250101000000Z -enddate 20350101000000Z
        // The private keys were discarded; only the certificates are here, as DER in Base64,
        // because a certificate file extension is a forbidden tree entry (tools/secret-scan).
        // LAPSED_TELEVISION is the same shape with its validity window in 2020.
        const val TELEVISION_ONE =
            "MIIBGDCBvwICEAEwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMYXBwdC10ZXN0LWNhMB4XDTI1MDEwMTAwMDAwMFoXDTM1MDEwMTAwMDAwMFowGTEXMBUGA1UEAwwOdGVsZXZpc2lvbi1vbmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASObuUNq6tAg3z1fdIvtT8AvXUbg//zuaRP41pxPY/KMLwUsUD9KNFARCcCwVVhOP3L6dZDDJYQcO/tJ1wf7SkHMAoGCCqGSM49BAMCA0gAMEUCIQC7I6MevYt9CNcH9RcgpD/7NBXWakjARYyQCDgWtzBVfwIgVlWkgsNSxI94RdMu7tWJ3UVPuy/AgzbHUF8HweGTqoo="
        const val TELEVISION_ONE_PIN =
            "8ba863ab981d3f0cad6568d6641fb69dce090bb095383f5aa81c46d1447bd840"
        const val TELEVISION_TWO =
            "MIIBGDCBvwICEAIwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMYXBwdC10ZXN0LWNhMB4XDTI1MDEwMTAwMDAwMFoXDTM1MDEwMTAwMDAwMFowGTEXMBUGA1UEAwwOdGVsZXZpc2lvbi10d28wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATGr1w2HahdnzYKKeTzKV8FQCGSmI/AoiWGUnE/u0F6BE2vHeXnT2ePgbaqwpcLFT8/1/hccdw7YwKRJLgCjev7MAoGCCqGSM49BAMCA0gAMEUCIQD/H7AUR1q0zVLexMiJjdWGrXSqHaLRg7TBJYYNJXEZVQIgFcQlUP0uDiqczyQpLmTn7hkNdHMXteybixXm3Cs8NAk="
        const val TELEVISION_TWO_PIN =
            "48e3a89bac920e7f653134c62c9b847d930de97a9f390dc4782becf768c7e89a"
        const val LAPSED_TELEVISION =
            "MIIBGzCBwgICEAMwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMYXBwdC10ZXN0LWNhMB4XDTIwMDEwMTAwMDAwMFoXDTIwMDEwMjAwMDAwMFowHDEaMBgGA1UEAwwRbGFwc2VkLXRlbGV2aXNpb24wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQLNW2AEsH/kaukWBHhcyHJYpgyrQeLakFQfwy9fdZ4TBpFV/+VskWeMasaUqm1J7G5GmtqpzRZBaG/wOmLX8WKMAoGCCqGSM49BAMCA0gAMEUCIBHuzo6dO32FvE5YI+I1/RCCSpATq1BNXyoMT3VWqAzMAiEA5jYEx6fYCY93+xFDuze1kc5u6Y8P+5zvsxRB8lSVdNs="
    }
}
