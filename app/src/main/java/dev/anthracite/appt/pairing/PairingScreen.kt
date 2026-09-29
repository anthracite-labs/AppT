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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onPairAgain: () -> Unit = {},
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
                PairingBody(state, onCancel, onRetryApproval, onPairAgain)
            }
        }
    }
}

@Composable
private fun PairingBody(
    state: PairingUiState,
    onCancel: () -> Unit,
    onRetryApproval: () -> Unit,
    onPairAgain: () -> Unit,
) {
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
            is PairingPhase.Failed -> PairingFailed(phase.failure, onRetryApproval, onPairAgain)
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
private fun PairingFailed(
    failure: TvFailure,
    onRetryApproval: () -> Unit,
    onPairAgain: () -> Unit,
) {
    val message =
        when (failure) {
            TvFailure.NeedsRepair -> R.string.pairing_failed_needs_repair
            TvFailure.Unreachable -> R.string.pairing_failed_unreachable
            TvFailure.Unsupported -> R.string.pairing_failed_unsupported
            TvFailure.SecretsUnavailable -> R.string.pairing_failed_secrets_unavailable
            TvFailure.IdentityChanged -> R.string.pairing_failed_identity_changed
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
        if (failure == TvFailure.NeedsRepair || failure == TvFailure.TimedOut) {
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
        // Saved material is never silently reset: pairing again is the user's explicit,
        // confirmed act — the dialog says what is removed and what comes next — and it is the
        // only repair this surface offers. Retry stays reserved for denied and timed-out
        // approvals; SecretsUnavailable and the identity failures go through here, never Retry.
        if (failure == TvFailure.SecretsUnavailable || failure == TvFailure.IdentityChanged) {
            PairAgainRepairControl(onPairAgain = onPairAgain)
        }
    }
}

/** The confirmed pair-again repair: the button opens the dialog, the dialog does the act. */
@Composable
private fun PairAgainRepairControl(onPairAgain: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Button(
        onClick = { confirming = true },
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = SizeTokens.primaryControl)
                .testTag(PairingTestTags.PAIR_AGAIN),
    ) {
        Text(text = stringResource(R.string.pairing_pair_again), style = TypeTokens.label)
    }
    if (confirming) {
        PairAgainConfirmationDialog(
            onDismiss = { confirming = false },
            onConfirm = {
                // The dialog is dismissed first, so it never outlives the act it confirmed.
                confirming = false
                onPairAgain()
            },
        )
    }
}

/**
 * The explicit confirmation the pair again owes the user (presentation.md): the dialog says what is
 * removed and what comes next, so the destructive act is never a single tap. Confirm dismisses the
 * dialog before the repair runs; the dialog never outlives the act it confirmed.
 */
@Composable
private fun PairAgainConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pairing_pair_again_confirm_title)) },
        text = { Text(text = stringResource(R.string.pairing_pair_again_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(PairingTestTags.PAIR_AGAIN_CONFIRM),
            ) {
                Text(text = stringResource(R.string.pairing_pair_again_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.pairing_pair_again_cancel))
            }
        },
    )
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
