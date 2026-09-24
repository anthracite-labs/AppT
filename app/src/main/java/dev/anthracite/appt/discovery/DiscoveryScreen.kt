package dev.anthracite.appt.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anthracite.appt.R
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/**
 * Discovery: televisions appear as cards while the bounded scan runs
 * (presentation.md#discovery, ui-ux.md).
 * * Compact width shows one column of cards; medium and expanded widths show a two-column grid.
 * * A card shows the television's friendly name and an ordinary-language state with a text label
 *   and an icon, never colour alone and never an address or identifier.
 * * `Unsupported` cards are not interactive: "AppT can't control this television yet".
 * * The empty state says "No televisions found" and offers Scan again.
 */
@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    onRescan: () -> Unit,
    onPick: (TvId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val columns = if (maxWidth >= MEDIUM_WIDTH) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().testTag(DiscoveryTestTags.LIST),
                contentPadding = PaddingValues(horizontal = SpaceTokens.lg, vertical = SpaceTokens.xl),
                verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
                horizontalArrangement = Arrangement.spacedBy(SpaceTokens.md),
            ) {
                fullWidth { Header(state.scan) }
                items(state.cards, key = { it.tvId.value }) { card -> TvCard(card, onPick) }
                footer(state, onRescan)
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.footer(state: DiscoveryUiState, onRescan: () -> Unit) {
    val scan = state.scan
    when {
        state.showEmptyState -> fullWidth { EmptyState(onRescan) }
        scan is ScanPhase.Failed -> fullWidth { Failure(scan.reason, onRescan) }
        scan == ScanPhase.Finished -> fullWidth { RescanButton(onRescan) }
        else -> Unit
    }
}

@Composable
private fun Header(scan: ScanPhase) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceTokens.sm)) {
        Text(
            text = stringResource(R.string.discovery_title),
            style = TypeTokens.display,
            color = ColorTokens.contentPrimary,
            modifier = Modifier.semantics { heading() }.testTag(DiscoveryTestTags.TITLE),
        )
        val status =
            when (scan) {
                ScanPhase.Scanning -> R.string.discovery_status_scanning
                ScanPhase.Finished -> R.string.discovery_status_finished
                is ScanPhase.Failed -> null
            }
        if (status != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpaceTokens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (scan == ScanPhase.Scanning) {
                    // Decorative: the status text beside it carries the meaning.
                    CircularProgressIndicator(
                        modifier = Modifier.size(SizeTokens.iconSmall).clearAndSetSemantics {},
                        color = ColorTokens.brandAccent,
                        strokeWidth = PROGRESS_STROKE,
                    )
                }
                Text(
                    text = stringResource(status),
                    style = TypeTokens.body,
                    color = ColorTokens.contentSecondary,
                    modifier =
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(DiscoveryTestTags.STATUS),
                )
            }
        }
    }
}

@Composable
private fun TvCard(card: TvCardUi, onPick: (TvId) -> Unit) {
    val label = card.label.ifBlank { stringResource(R.string.discovery_card_unnamed) }
    val interactive =
        if (card.state == CardState.Unsupported) {
            // No control affordance on an Unsupported card: not clickable, no button.
            Modifier
        } else {
            Modifier.clickable(
                onClickLabel = stringResource(R.string.discovery_card_choose),
                role = Role.Button,
            ) {
                onPick(card.tvId)
            }
        }
    val shape = RoundedCornerShape(SpaceTokens.md)
    Surface(
        color = ColorTokens.surfaceElevated,
        border = BorderStroke(CARD_BORDER, ColorTokens.surfaceOutline),
        shape = shape,
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = SizeTokens.minimumTouchTarget)
                .clip(shape)
                .then(interactive)
                .testTag(DiscoveryTestTags.CARD),
    ) {
        Column(
            modifier = Modifier.padding(SpaceTokens.md),
            verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs),
        ) {
            Text(
                text = label,
                style = TypeTokens.title,
                color = ColorTokens.contentPrimary,
                modifier = Modifier.testTag(DiscoveryTestTags.CARD_LABEL),
            )
            CardStatus(card.state)
            if (card.remembered) {
                Text(
                    text = stringResource(R.string.discovery_card_remembered),
                    style = TypeTokens.label,
                    color = ColorTokens.contentSecondary,
                )
            }
        }
    }
}

@Composable
private fun CardStatus(state: CardState) {
    val (glyph, text, color) =
        when (state) {
            CardState.Ready ->
                Triple(R.string.discovery_status_glyph_ready, R.string.discovery_card_ready, ColorTokens.statusReady)
            CardState.NeedsPairing ->
                Triple(
                    R.string.discovery_status_glyph_needs_pairing,
                    R.string.discovery_card_needs_pairing,
                    ColorTokens.statusAttention,
                )
            CardState.Unsupported ->
                Triple(
                    R.string.discovery_status_glyph_unsupported,
                    R.string.discovery_card_unsupported,
                    ColorTokens.statusUnavailable,
                )
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpaceTokens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusGlyph(stringResource(glyph), color)
        Text(
            text = stringResource(text),
            style = TypeTokens.label,
            color = ColorTokens.contentSecondary,
            modifier = Modifier.testTag(DiscoveryTestTags.CARD_STATUS_LABEL),
        )
    }
}

/** The status icon: a distinct shape per state, so state is never carried by colour alone. */
@Composable
private fun StatusGlyph(glyph: String, color: Color) {
    Text(
        text = glyph,
        style = TypeTokens.label,
        color = color,
        modifier = Modifier.testTag(DiscoveryTestTags.CARD_STATUS_ICON).clearAndSetSemantics {},
    )
}

@Composable
private fun EmptyState(onRescan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(DiscoveryTestTags.EMPTY_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
    ) {
        // Brand mascot: decorative, hidden from the semantics tree.
        Text(
            text = stringResource(R.string.welcome_mascot_glyph),
            style = TypeTokens.display,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = stringResource(R.string.discovery_empty_title),
            style = TypeTokens.title,
            color = ColorTokens.contentPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.discovery_empty_hint),
            style = TypeTokens.body,
            color = ColorTokens.contentSecondary,
        )
        RescanButton(onRescan)
    }
}

@Composable
private fun Failure(reason: TvFailure, onRescan: () -> Unit) {
    val message =
        when (reason) {
            TvFailure.Unreachable -> R.string.discovery_failed_unreachable
            TvFailure.LocalNetworkDenied -> R.string.discovery_failed_denied
            else -> R.string.discovery_failed_other
        }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(DiscoveryTestTags.FAILURE),
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
    ) {
        Text(
            text = stringResource(message),
            style = TypeTokens.body,
            color = ColorTokens.feedbackWarning,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        RescanButton(onRescan)
    }
}

@Composable
private fun RescanButton(onRescan: () -> Unit) {
    Button(
        onClick = onRescan,
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minWidth = SizeTokens.minimumTouchTarget, minHeight = SizeTokens.primaryControl)
                .testTag(DiscoveryTestTags.RESCAN),
    ) {
        Text(text = stringResource(R.string.discovery_rescan), style = TypeTokens.label)
    }
}

/** presentation.md: medium width starts at 600dp, where Discovery becomes a two-column grid. */
private val MEDIUM_WIDTH = 600.dp
private val CARD_BORDER = 1.dp
private val PROGRESS_STROKE = 2.dp

@Preview(showBackground = true)
@Composable
private fun DiscoveryScreenPreview() {
    AppTTheme {
        DiscoveryScreen(
            state =
                DiscoveryUiState(
                    scan = ScanPhase.Scanning,
                    cards =
                        listOf(
                            TvCardUi(TvId("preview-1"), "Living Room TV", CardState.NeedsPairing, remembered = false),
                            TvCardUi(TvId("preview-2"), "Older TV", CardState.Unsupported, remembered = false),
                        ),
                    showEmptyState = false,
                ),
            onRescan = {},
            onPick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryEmptyPreview() {
    AppTTheme {
        DiscoveryScreen(
            state = DiscoveryUiState(scan = ScanPhase.Finished, cards = emptyList(), showEmptyState = true),
            onRescan = {},
            onPick = {},
        )
    }
}
