package dev.anthracite.appt.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anthracite.appt.R
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/**
 * Pairing: one focused state that tells the user to allow AppT on the television
 * (presentation.md#pairing, ui-ux.md).
 *
 * The copy names the television and directs the user to it. It carries no port, certificate,
 * transport, token or protocol-generation vocabulary, offers Cancel, and moves to Remote only when
 * the session is `Ready` — that transition is the screen's caller's decision, not this surface's.
 */
@Composable
fun PairingScreen(
    state: PairingUiState,
    onCancel: () -> Unit,
    onRetryApproval: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .padding(SpaceTokens.lg)
                    .widthIn(max = SizeTokens.readableContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        ) {
            Text(
                text = stringResource(R.string.pairing_title),
                style = TypeTokens.display,
                color = ColorTokens.contentPrimary,
                modifier = Modifier.semantics { heading() }.testTag(PairingTestTags.TITLE),
            )
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PairingBody(state, onCancel, onRetryApproval)
            }
        }
    }
}

@Composable
private fun PairingBody(state: PairingUiState, onCancel: () -> Unit, onRetryApproval: () -> Unit) {
    val television = state.tvName.ifBlank { stringResource(R.string.pairing_tv_unnamed) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(PairingTestTags.BODY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
    ) {
        when (val phase = state.phase) {
            PairingPhase.Connecting -> PairingConnecting()
            PairingPhase.WaitingForApproval ->
                PairingWaiting(television = television, recallHintVisible = state.recallHintVisible)
            PairingPhase.Succeeded -> PairingSucceeded(television)
            is PairingPhase.Failed -> PairingFailed(phase.failure, onRetryApproval)
        }
        // Cancel is always available: leaving a pairing attempt must never be a trap.
        OutlinedButton(
            onClick = onCancel,
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = SizeTokens.primaryControl)
                    .testTag(PairingTestTags.CANCEL),
        ) {
            Text(text = stringResource(R.string.pairing_cancel), style = TypeTokens.label)
        }
    }
}

@Composable
private fun PairingConnecting() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        modifier = Modifier.testTag(PairingTestTags.CONNECTING),
    ) {
        // Decorative: the status text beside it carries the meaning.
        CircularProgressIndicator(
            modifier = Modifier.size(SizeTokens.iconSmall).clearAndSetSemantics {},
            color = ColorTokens.brandAccent,
            strokeWidth = PROGRESS_STROKE,
        )
        PairingStatus(stringResource(R.string.pairing_connecting))
    }
}

@Composable
private fun PairingWaiting(television: String, recallHintVisible: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        modifier = Modifier.fillMaxWidth().testTag(PairingTestTags.WAITING),
    ) {
        Text(
            text = stringResource(R.string.pairing_waiting, television),
            style = TypeTokens.title,
            color = ColorTokens.contentPrimary,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PairingTestTags.INSTRUCTION),
        )
        if (recallHintVisible) {
            Text(
                text = stringResource(R.string.pairing_recall_hint),
                style = TypeTokens.body,
                color = ColorTokens.contentSecondary,
                modifier = Modifier.testTag(PairingTestTags.RECALL_HINT),
            )
        }
        // Decorative: the instruction above is the spoken prompt.
        CircularProgressIndicator(
            modifier = Modifier.size(SizeTokens.iconSmall).clearAndSetSemantics {},
            color = ColorTokens.brandAccent,
            strokeWidth = PROGRESS_STROKE,
        )
    }
}

@Composable
private fun PairingSucceeded(television: String) {
    PairingStatus(
        stringResource(R.string.pairing_succeeded, television),
        tag = PairingTestTags.SUCCEEDED,
    )
}

@Composable
private fun PairingFailed(failure: TvFailure, onRetryApproval: () -> Unit) {
    val message =
        when (failure) {
            TvFailure.NeedsRepair -> R.string.pairing_failed_needs_repair
            TvFailure.Unreachable -> R.string.pairing_failed_unreachable
            TvFailure.Unsupported -> R.string.pairing_failed_unsupported
            else -> R.string.pairing_failed_unavailable
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        modifier = Modifier.fillMaxWidth().testTag(PairingTestTags.FAILED),
    ) {
        Text(
            text = stringResource(message),
            style = TypeTokens.body,
            color = ColorTokens.feedbackWarning,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PairingTestTags.FAILURE_MESSAGE),
        )
        // Only a denied or timed-out approval can be retried; the reason is already in the copy.
        if (failure == TvFailure.NeedsRepair) {
            Button(
                onClick = onRetryApproval,
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = SizeTokens.primaryControl)
                        .testTag(PairingTestTags.RETRY),
            ) {
                Text(text = stringResource(R.string.pairing_retry), style = TypeTokens.label)
            }
        }
    }
}

@Composable
private fun PairingStatus(text: String, tag: String = PairingTestTags.STATUS) {
    Text(
        text = text,
        style = TypeTokens.body,
        color = ColorTokens.contentSecondary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}

@Preview(showBackground = true)
@Composable
private fun PairingWaitingPreview() {
    AppTTheme {
        PairingScreen(
            state =
                PairingUiState(
                    tvName = "Living Room TV",
                    phase = PairingPhase.WaitingForApproval,
                    recallHintVisible = true,
                ),
            onCancel = {},
            onRetryApproval = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PairingFailedPreview() {
    AppTTheme {
        PairingScreen(
            state =
                PairingUiState(
                    tvName = "Living Room TV",
                    phase = PairingPhase.Failed(TvFailure.NeedsRepair),
                    recallHintVisible = false,
                ),
            onCancel = {},
            onRetryApproval = {},
        )
    }
}

private val PROGRESS_STROKE = 2.dp
