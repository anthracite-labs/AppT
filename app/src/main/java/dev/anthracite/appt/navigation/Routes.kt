package dev.anthracite.appt.navigation

import kotlinx.serialization.Serializable

/**
 * The AppT navigation graph.
 *
 * presentation.md#route-graph describes the eventual route set. S01's graph
 * contains exactly one destination, [WelcomeRoute], and no later-slice route
 * is declared here to make navigation "look complete": a route exists in the
 * slice that renders it.
 */
@Serializable
data object WelcomeRoute
