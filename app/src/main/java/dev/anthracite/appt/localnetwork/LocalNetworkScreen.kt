package dev.anthracite.appt.localnetwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.anthracite.appt.R
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/**
 * The local-network explanation, shown immediately before the first scan
 * (docs/architecture/discovery.md#permission-gate, ui-ux.md#onboarding).
 *
 * Ordinary language only: the copy says AppT needs to reach the television on the home network and
 * never names a protocol, a port, or an address. Stateless: it renders [state] and forwards
 * intents.
 */
@Composable
fun LocalNetworkScreen(
    state: LocalNetworkUiState,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SpaceTokens.lg, vertical = SpaceTokens.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceTokens.md, Alignment.CenterVertically),
        ) {
            Explanation()
            when (state.phase) {
                LocalNetworkPhase.Explain ->
                    PrimaryButton(
                        label = stringResource(R.string.localnetwork_continue),
                        onClick = onContinue,
                        tag = LocalNetworkTestTags.CONTINUE,
                    )
                LocalNetworkPhase.Denied ->
                    DeniedActions(
                        state.canOpenAppSettings,
                        onRetry = onRetry,
                        onOpenSettings = onOpenSettings,
                    )
                // Requesting is never entered by the V1 gate, and Granted navigates to Discovery
                // straight away; neither offers an action of its own.
                LocalNetworkPhase.Requesting,
                LocalNetworkPhase.Granted -> Unit
            }
        }
    }
}

@Composable
private fun Explanation() {
    Text(
        text = stringResource(R.string.localnetwork_title),
        style = TypeTokens.display,
        color = ColorTokens.contentPrimary,
        modifier =
            Modifier.widthIn(max = SizeTokens.readableContentMaxWidth)
                .semantics { heading() }
                .testTag(LocalNetworkTestTags.TITLE),
    )
    Text(
        text = stringResource(R.string.localnetwork_explanation),
        style = TypeTokens.body,
        color = ColorTokens.contentSecondary,
        modifier =
            Modifier.widthIn(max = SizeTokens.readableContentMaxWidth)
                .testTag(LocalNetworkTestTags.EXPLANATION),
    )
}

@Composable
private fun DeniedActions(
    canOpenAppSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text(
        text = stringResource(R.string.localnetwork_denied),
        style = TypeTokens.body,
        color = ColorTokens.feedbackWarning,
        modifier =
            Modifier.widthIn(max = SizeTokens.readableContentMaxWidth)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(LocalNetworkTestTags.DENIED_MESSAGE),
    )
    PrimaryButton(
        label = stringResource(R.string.localnetwork_retry),
        onClick = onRetry,
        tag = LocalNetworkTestTags.RETRY,
    )
    if (canOpenAppSettings) {
        OutlinedButton(
            onClick = onOpenSettings,
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = SizeTokens.readableContentMaxWidth)
                    .defaultMinSize(minHeight = SizeTokens.minimumTouchTarget)
                    .testTag(LocalNetworkTestTags.OPEN_SETTINGS),
        ) {
            Text(
                text = stringResource(R.string.localnetwork_open_settings),
                style = TypeTokens.label,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, tag: String) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = SizeTokens.readableContentMaxWidth)
                .defaultMinSize(
                    minWidth = SizeTokens.minimumTouchTarget,
                    minHeight = SizeTokens.primaryControl,
                )
                .testTag(tag),
    ) {
        Text(text = label, style = TypeTokens.label)
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalNetworkScreenPreview() {
    AppTTheme {
        LocalNetworkScreen(
            state = LocalNetworkUiState.of(LocalNetworkPhase.Explain),
            onContinue = {},
            onRetry = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalNetworkScreenDeniedPreview() {
    AppTTheme {
        LocalNetworkScreen(
            state = LocalNetworkUiState.of(LocalNetworkPhase.Denied),
            onContinue = {},
            onRetry = {},
            onOpenSettings = {},
        )
    }
}
