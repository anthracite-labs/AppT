package dev.anthracite.appt.navigation

import kotlinx.serialization.Serializable

/**
 * The AppT navigation graph routes (presentation.md#route-graph).
 *
 * A route exists in the slice that renders it. S02 adds the local-network explanation and
 * Discovery; S03 (Issue #79) adds Pairing, which asks the user to allow AppT on the television, and
 * the Remote surface that session hands off to. The rest arrive with their slices.
 */
@Serializable data object WelcomeRoute

/** The local-network explanation, shown immediately before the first scan. */
@Serializable data object LocalNetworkRoute

/** Discovery: starts one bounded scan as soon as it is shown. */
@Serializable data object DiscoveryRoute

/**
 * Pairing: a dedicated focused state for one television (presentation.md#pairing).
 *
 * The television is named in ordinary language; the argument is the opaque [tvId] and is never
 * rendered as an address, port, or identifier.
 */
@Serializable data class PairingRoute(val tvId: String)

/** The remote surface for one television, reached from Pairing once the session is `Ready`. */
@Serializable data class RemoteRoute(val tvId: String)
