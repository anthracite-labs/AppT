package dev.anthracite.appt.discovery

import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId

/**
 * presentation.md#discovery: `DiscoveryUiState(scan: Scanning | Finished | Failed(reason), cards:
 * List<TvCardUi>, showEmptyState)`.
 */
data class DiscoveryUiState(
    val scan: ScanPhase,
    val cards: List<TvCardUi>,
    val showEmptyState: Boolean,
) {
    /** Folds one discovery event into the state. Pure, so every transition is a unit test. */
    fun reduce(event: DiscoveryEvent): DiscoveryUiState =
        when (event) {
            is DiscoveryEvent.Found ->
                if (cards.any { it.tvId == event.tv.id }) this
                else copy(cards = cards + TvCardUi.of(event.tv))
            DiscoveryEvent.Finished ->
                copy(scan = ScanPhase.Finished, showEmptyState = cards.isEmpty())
            is DiscoveryEvent.Failed ->
                copy(scan = ScanPhase.Failed(event.failure), showEmptyState = false)
        }

    companion object {
        val Initial: DiscoveryUiState =
            DiscoveryUiState(scan = ScanPhase.Scanning, cards = emptyList(), showEmptyState = false)
    }
}

sealed interface ScanPhase {
    data object Scanning : ScanPhase

    data object Finished : ScanPhase

    data class Failed(val reason: TvFailure) : ScanPhase
}

/**
 * One television card: a friendly label and an ordinary-language state, never an address, port,
 * UUID, MAC, or protocol generation. [tvId] is opaque and is never rendered.
 *
 * @property label the television's own name; blank when it reports none or when the name carries an
 *   identifier ([DisplayLabel]), in which case the screen shows a localized generic label rather
 *   than anything technical.
 */
data class TvCardUi(
    val tvId: TvId,
    val label: String,
    val state: CardState,
    val remembered: Boolean,
) {
    companion object {
        fun of(tv: DiscoveredTv): TvCardUi =
            TvCardUi(
                tvId = tv.id,
                label = DisplayLabel.of(tv.name),
                state =
                    when (tv.availability) {
                        ControlAvailability.ReadyToOpen -> CardState.Ready
                        ControlAvailability.NeedsPairing -> CardState.NeedsPairing
                        ControlAvailability.Unsupported -> CardState.Unsupported
                    },
                remembered = tv.remembered,
            )
    }
}

enum class CardState {
    Ready,
    NeedsPairing,
    Unsupported,
}
