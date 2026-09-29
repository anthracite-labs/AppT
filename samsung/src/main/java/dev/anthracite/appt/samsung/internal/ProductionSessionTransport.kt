package dev.anthracite.appt.samsung.internal

/**
 * Production [SessionTransport]: TLS port 8002 on OkHttp, plaintext port 8001 on a bounded raw
 * socket (docs/architecture/modules.md#internal-seams-inside-samsung).
 *
 * The split is the Issue #79 revision-2 decision: keep the adopted 8001 fallback without enabling
 * application-wide cleartext traffic. The saved pairing travels with the attempt so the TLS adapter
 * can enforce the saved SPKI pin before any token is attached
 * (docs/architecture/connection.md#ordering-relative-to-secrets); the plaintext adapter ignores it,
 * because that channel never carries a token.
 */
internal class ProductionSessionTransport(
    private val tls: SessionTransport = OkHttpSessionTransport(),
    private val plaintext: SessionTransport = PlaintextWebSocketTransport(),
) : SessionTransport {
    override suspend fun connect(
        television: ConfirmedTelevision,
        saved: PairingSecret?,
    ): ConnectionAttempt {
        val adapter = if (television.tls) tls else plaintext
        return adapter.connect(television, saved)
    }
}
