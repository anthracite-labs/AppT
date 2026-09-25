package dev.anthracite.appt.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.anthracite.appt.R
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/**
 * Remote: the first live control surface (presentation.md#remote, ui-ux.md).
 * * Only the keys in [RemoteUiState.keys] are rendered. Capability evidence drives the layout, so a
 *   key the television has not accepted is hidden rather than shown and failing.
 * * Every target is at least 48dp, every control has a spoken label and a role, and the connection
 *   status is always text beside the television's name, never colour alone.
 * * No purchase, account, or trial surface appears here.
 */
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    onCommand: (TvCommand) -> Unit,
    onRetry: () -> Unit,
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
            RemoteHeader(state)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (val connection = state.connection) {
                    ConnectionUi.Ready -> RemoteControls(state.keys, onCommand)
                    ConnectionUi.Connecting,
                    ConnectionUi.WaitingForApproval -> RemoteStatus(connection.statusText())
                    is ConnectionUi.NeedsRepair ->
                        RemoteRecovery(connection.statusText(), onRetry = onRetry)
                    is ConnectionUi.Unavailable ->
                        RemoteRecovery(connection.statusText(), onRetry = onRetry)
                    ConnectionUi.Unsupported -> RemoteStatus(connection.statusText())
                }
            }
        }
    }
}

@Composable
private fun RemoteHeader(state: RemoteUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs)) {
        Text(
            text = state.tvName.ifBlank { stringResource(R.string.remote_tv_unnamed) },
            style = TypeTokens.title,
            color = ColorTokens.contentPrimary,
            modifier = Modifier.semantics { heading() }.testTag(RemoteTestTags.TV_NAME),
        )
        Text(
            text = state.connection.statusText(),
            style = TypeTokens.label,
            color = ColorTokens.contentSecondary,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(RemoteTestTags.STATUS),
        )
    }
}

@Composable
private fun RemoteStatus(message: String) {
    Text(
        text = message,
        style = TypeTokens.body,
        color = ColorTokens.contentSecondary,
        modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(RemoteTestTags.STATUS),
    )
}

@Composable
private fun RemoteRecovery(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        modifier = Modifier.fillMaxWidth().testTag(RemoteTestTags.RECOVERY),
    ) {
        Text(
            text = message,
            style = TypeTokens.body,
            color = ColorTokens.feedbackWarning,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(RemoteTestTags.STATUS),
        )
        Button(
            onClick = onRetry,
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = SizeTokens.primaryControl)
                    .testTag(RemoteTestTags.RETRY),
        ) {
            Text(text = stringResource(R.string.remote_retry), style = TypeTokens.label)
        }
    }
}

/** The minimal control set: a volume row, a directional pad, and two chrome keys. */
@Composable
private fun RemoteControls(keys: List<RemoteKey>, onCommand: (TvCommand) -> Unit) {
    val available = keys.toSet()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.xl),
        modifier = Modifier.fillMaxWidth().testTag(RemoteTestTags.CONTROLS),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpaceTokens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MINIMAL_REMOTE_KEYS.take(VOLUME_ROW).forEach { key ->
                RemoteKeyButton(key, available, onCommand)
            }
        }
        DirectionalPad(available, onCommand)
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceTokens.md)) {
            MINIMAL_REMOTE_KEYS.drop(VOLUME_ROW + PAD_KEYS).forEach { key ->
                RemoteKeyButton(key, available, onCommand)
            }
        }
    }
}

/** The four directions and the centre select, laid out as a cross. */
@Composable
private fun DirectionalPad(available: Set<RemoteKey>, onCommand: (TvCommand) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs),
    ) {
        RemoteKeyButton(RemoteKey.Up, available, onCommand)
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpaceTokens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteKeyButton(RemoteKey.Left, available, onCommand)
            RemoteKeyButton(RemoteKey.Enter, available, onCommand)
            RemoteKeyButton(RemoteKey.Right, available, onCommand)
        }
        RemoteKeyButton(RemoteKey.Down, available, onCommand)
    }
}

/**
 * One control. A key the television has not accepted is not rendered at all, so no dead button is
 * shown and no gesture is offered without a visible alternative.
 */
@Composable
private fun RemoteKeyButton(
    key: RemoteKey,
    available: Set<RemoteKey>,
    onCommand: (TvCommand) -> Unit,
) {
    if (key !in available) return
    Button(
        onClick = { onCommand(TvCommand.Tap(key)) },
        modifier =
            Modifier.defaultMinSize(
                    minWidth = SizeTokens.minimumTouchTarget,
                    minHeight = SizeTokens.minimumTouchTarget,
                )
                .semantics { role = Role.Button }
                .testTag(RemoteTestTags.key(key)),
    ) {
        Text(text = stringResource(key.label()), style = TypeTokens.label)
    }
}

@Composable
private fun ConnectionUi.statusText(): String =
    when (this) {
        ConnectionUi.Connecting -> stringResource(R.string.remote_connecting)
        ConnectionUi.WaitingForApproval -> stringResource(R.string.remote_waiting)
        ConnectionUi.Ready -> stringResource(R.string.remote_ready)
        is ConnectionUi.NeedsRepair -> stringResource(failure.message())
        is ConnectionUi.Unavailable -> stringResource(failure.message())
        ConnectionUi.Unsupported -> stringResource(R.string.remote_unsupported)
    }

@Composable
private fun RemoteKey.label(): Int =
    when (this) {
        RemoteKey.VolumeUp -> R.string.remote_volume_up
        RemoteKey.VolumeDown -> R.string.remote_volume_down
        RemoteKey.Mute -> R.string.remote_mute
        RemoteKey.Up -> R.string.remote_direction_up
        RemoteKey.Down -> R.string.remote_direction_down
        RemoteKey.Left -> R.string.remote_direction_left
        RemoteKey.Right -> R.string.remote_direction_right
        RemoteKey.Enter -> R.string.remote_enter
        RemoteKey.Back -> R.string.remote_back
        RemoteKey.Home -> R.string.remote_home
        RemoteKey.Power -> R.string.remote_power
    }

@Composable
private fun TvFailure.message(): Int =
    when (this) {
        TvFailure.NeedsRepair -> R.string.remote_needs_repair
        TvFailure.Unreachable -> R.string.remote_unreachable
        TvFailure.Unsupported -> R.string.remote_unsupported
        TvFailure.TimedOut -> R.string.remote_needs_repair
        else -> R.string.remote_unavailable
    }

@Preview(showBackground = true)
@Composable
private fun RemoteReadyPreview() {
    AppTTheme {
        RemoteScreen(
            state =
                RemoteUiState(
                    tvName = "Living Room TV",
                    connection = ConnectionUi.Ready,
                    keys = MINIMAL_REMOTE_KEYS,
                ),
            onCommand = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoteWaitingPreview() {
    AppTTheme {
        RemoteScreen(
            state =
                RemoteUiState(
                    tvName = "Living Room TV",
                    connection = ConnectionUi.WaitingForApproval,
                    keys = emptyList(),
                ),
            onCommand = {},
            onRetry = {},
        )
    }
}

/** The volume row: up, down, mute. */
private const val VOLUME_ROW = 3

/** The directional pad: up, left, enter, right, down. */
private const val PAD_KEYS = 5
