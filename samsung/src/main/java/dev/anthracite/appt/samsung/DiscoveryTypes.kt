package dev.anthracite.appt.samsung

// Caller-facing types for discovery, verbatim from docs/architecture/samsung-interface.md.
// No caller-facing type contains an address, MAC, token, certificate, Wi-Fi name, or raw payload.

/**
 * Opaque television identity. Callers do not parse it for a MAC, an address, or a protocol
 * generation.
 */
@JvmInline value class TvId(val value: String)

/**
 * A confirmed television.
 *
 * @property name the television's own friendly name, trimmed and capped at 40 characters. It may be
 *   empty when the television reports none; the caller then shows its own localized label and never
 *   an address.
 * @property remembered true when a samsung-private record exists for [id]. S02 keeps no record, so
 *   discovery reports `false` until S04 adds the store.
 * @property stableIdentity true only when the television itself supplied [id]; false when the id
 *   was minted on this phone for a television that exposes no stable identity.
 */
data class DiscoveredTv(
    val id: TvId,
    val name: String,
    val remembered: Boolean,
    val stableIdentity: Boolean,
    val availability: ControlAvailability,
)

enum class ControlAvailability {
    NeedsPairing,
    ReadyToOpen,
    Unsupported,
}

sealed interface DiscoveryEvent {
    data class Found(val tv: DiscoveredTv) : DiscoveryEvent

    data object Finished : DiscoveryEvent

    data class Failed(val failure: TvFailure) : DiscoveryEvent
}

sealed interface TvFailure {
    data object LocalNetworkDenied : TvFailure

    data object Unreachable : TvFailure

    data object TimedOut : TvFailure

    data object NeedsRepair : TvFailure

    data object Unsupported : TvFailure

    data object Rejected : TvFailure

    data object Unavailable : TvFailure

    data object SecretsUnavailable : TvFailure
}
