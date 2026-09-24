package dev.anthracite.appt.navigation

import kotlinx.serialization.Serializable

/**
 * The AppT navigation graph routes (presentation.md#route-graph).
 *
 * A route exists in the slice that renders it. S02 adds the local-network explanation and
 * Discovery; Pairing, Remote and the rest arrive with their slices.
 */
@Serializable data object WelcomeRoute

/** The local-network explanation, shown immediately before the first scan. */
@Serializable data object LocalNetworkRoute

/** Discovery: starts one bounded scan as soon as it is shown. */
@Serializable data object DiscoveryRoute
