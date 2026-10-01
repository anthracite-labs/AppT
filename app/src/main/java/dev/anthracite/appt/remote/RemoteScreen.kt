package dev.anthracite.appt.remote

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anthracite.appt.R
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/** Phone-native, capability-driven Remote. Power is isolated in the chrome zone. */
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    onCommand: (TvCommand) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmRepair: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleNavigationMode: () -> Unit = {},
    onHapticFeedback: () -> Unit = {},
    readyAccessory: @Composable () -> Unit = {},
) {
    val haptic = remoteHaptic(state.hapticsEnabled, onHapticFeedback)
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = SpaceTokens.md, vertical = SpaceTokens.sm)
                        .widthIn(max = if (maxWidth >= 600.dp) 520.dp else maxWidth)
                        .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(SpaceTokens.sm),
            ) {
                RemoteHeader(
                    state = state,
                    readyAccessory = {
                        if (state.connection == ConnectionUi.Ready) readyAccessory()
                    },
                    onOpenSettings = {
                        haptic()
                        onOpenSettings()
                    },
                    onPower = {
                        haptic()
                        onCommand(TvCommand.Tap(RemoteKey.Power))
                    },
                )
                when (val connection = state.connection) {
                    ConnectionUi.Ready ->
                        RemoteControlZones(
                            state = state,
                            onCommand = onCommand,
                            onToggleNavigationMode = {
                                haptic()
                                onToggleNavigationMode()
                            },
                            haptic = haptic,
                        )
                    ConnectionUi.Connecting,
                    ConnectionUi.WaitingForApproval,
                    ConnectionUi.Reconnecting ->
                        RemoteStatus(connection.statusText(), connection.statusGlyph())
                    is ConnectionUi.NeedsRepair ->
                        if (connection.failure == TvFailure.IdentityChanged) {
                            RemoteRepair(connection.statusText(), onConfirmRepair, haptic)
                        } else {
                            RemoteRecovery(connection.statusText(), onRetry, haptic)
                        }
                    is ConnectionUi.Unavailable ->
                        RemoteRecovery(connection.statusText(), onRetry, haptic)
                    ConnectionUi.Unsupported -> RemoteStatus(connection.statusText(), "⊘")
                }
            }
        }
    }
}

@Composable
private fun remoteHaptic(
    enabled: Boolean,
    onHapticFeedback: () -> Unit,
): () -> Unit {
    val view = LocalView.current
    return {
        if (enabled && view.isHapticFeedbackEnabled) {
            // View.performHapticFeedback observes Android's system touch-feedback policy.
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onHapticFeedback()
        }
    }
}

@Composable
private fun RemoteHeader(
    state: RemoteUiState,
    readyAccessory: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    onPower: () -> Unit,
) {
    val statusText = state.connection.statusText()
    val statusGlyph = state.connection.statusGlyph()
    Column(verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceTokens.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.tvName.ifBlank { stringResource(R.string.remote_tv_unnamed) },
                    style = TypeTokens.title,
                    color = ColorTokens.contentPrimary,
                    modifier = Modifier.semantics { heading() }.testTag(RemoteTestTags.TV_NAME),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceTokens.xs)) {
                    Text(
                        text = statusGlyph,
                        color = ColorTokens.contentSecondary,
                        modifier = Modifier.semantics { contentDescription = statusText },
                    )
                    Text(
                        text = statusText,
                        style = TypeTokens.label,
                        color = ColorTokens.contentSecondary,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag(RemoteTestTags.STATUS),
                    )
                }
            }
            RemoteSettingsButton(onOpenSettings)
            if (state.powerOffAvailable) RemotePowerButton(onPower)
        }
        readyAccessory()
    }
}

@Composable
private fun RemoteSettingsButton(onOpenSettings: () -> Unit) {
    OutlinedButton(
        onClick = onOpenSettings,
        modifier =
            Modifier.defaultMinSize(
                    minWidth = SizeTokens.minimumTouchTarget,
                    minHeight = SizeTokens.minimumTouchTarget,
                )
                .testTag(RemoteTestTags.SETTINGS),
    ) {
        Text(stringResource(R.string.remote_settings), style = TypeTokens.label)
    }
}

@Composable
private fun RemotePowerButton(onPower: () -> Unit) {
    Button(
        onClick = onPower,
        modifier =
            Modifier.defaultMinSize(
                    minWidth = SizeTokens.primaryControl,
                    minHeight = SizeTokens.primaryControl,
                )
                .testTag(RemoteTestTags.key(RemoteKey.Power)),
    ) {
        Text(stringResource(R.string.remote_power), style = TypeTokens.label)
    }
}

@Composable
private fun RemoteControlZones(
    state: RemoteUiState,
    onCommand: (TvCommand) -> Unit,
    onToggleNavigationMode: () -> Unit,
    haptic: () -> Unit,
) {
    val available = state.keys.toSet()
    Column(
        modifier = Modifier.fillMaxSize().testTag(RemoteTestTags.CONTROLS),
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.pointerAvailable) {
            Button(
                onClick = onToggleNavigationMode,
                modifier =
                    Modifier.defaultMinSize(
                            minWidth = SizeTokens.minimumTouchTarget,
                            minHeight = SizeTokens.minimumTouchTarget,
                        )
                        .testTag(RemoteTestTags.NAVIGATION_MODE),
            ) {
                Text(
                    text =
                        if (state.navigationMode == NavigationMode.Pointer) {
                            stringResource(R.string.remote_use_directional)
                        } else {
                            stringResource(R.string.remote_use_touchpad)
                        },
                    style = TypeTokens.label,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (state.navigationMode == NavigationMode.Pointer && state.pointerAvailable) {
                Touchpad(onCommand = onCommand, haptic = haptic, modifier = Modifier.fillMaxWidth())
            } else {
                DirectionalPad(available, onCommand, haptic)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(SpaceTokens.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIGH_FREQUENCY_KEYS.forEach { key ->
                RemoteKeyButton(key, available, onCommand, haptic)
            }
        }
    }
}

@Composable
private fun RemoteStatus(message: String, glyph: String) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(RemoteTestTags.STATUS_PANEL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, style = TypeTokens.title, color = ColorTokens.contentSecondary)
        Text(
            text = message,
            style = TypeTokens.body,
            color = ColorTokens.contentSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun RemoteRecovery(message: String, onRetry: () -> Unit, haptic: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(RemoteTestTags.RECOVERY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("!", style = TypeTokens.title, color = ColorTokens.feedbackWarning)
        Text(message, style = TypeTokens.body, color = ColorTokens.contentPrimary)
        Button(
            onClick = {
                haptic()
                onRetry()
            },
            modifier =
                Modifier.padding(top = SpaceTokens.md)
                    .defaultMinSize(
                        minWidth = SizeTokens.primaryControl,
                        minHeight = SizeTokens.primaryControl,
                    )
                    .testTag(RemoteTestTags.RETRY),
        ) {
            Text(text = stringResource(R.string.remote_retry), style = TypeTokens.label)
        }
    }
}

/** Saved-identity repair remains behind the explicit S04 confirmation. */
@Composable
private fun RemoteRepair(statusText: String, onConfirmRepair: () -> Unit, haptic: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        modifier = Modifier.fillMaxSize().testTag(RemoteTestTags.RECOVERY),
    ) {
        Text("!", style = TypeTokens.title, color = ColorTokens.feedbackWarning)
        Text(statusText, style = TypeTokens.body, color = ColorTokens.contentPrimary)
        Button(
            onClick = {
                haptic()
                confirming = true
            },
            modifier =
                Modifier.defaultMinSize(
                    minWidth = SizeTokens.primaryControl,
                    minHeight = SizeTokens.primaryControl,
                ),
        ) {
            Text(text = stringResource(R.string.remote_repair), style = TypeTokens.label)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.remote_repair_confirm_title)) },
            text = { Text(stringResource(R.string.remote_repair_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        haptic()
                        confirming = false
                        onConfirmRepair()
                    },
                    modifier = Modifier.testTag(RemoteTestTags.REPAIR_CONFIRM),
                ) {
                    Text(stringResource(R.string.remote_repair_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirming = false },
                    modifier = Modifier.testTag(RemoteTestTags.REPAIR_CANCEL),
                ) {
                    Text(stringResource(R.string.remote_repair_cancel))
                }
            },
        )
    }
}

@Composable
private fun DirectionalPad(
    available: Set<RemoteKey>,
    onCommand: (TvCommand) -> Unit,
    haptic: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs),
        modifier = Modifier.testTag(RemoteTestTags.DIRECTIONAL_PAD),
    ) {
        RemoteKeyButton(RemoteKey.Up, available, onCommand, haptic)
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceTokens.xs)) {
            RemoteKeyButton(RemoteKey.Left, available, onCommand, haptic)
            RemoteKeyButton(RemoteKey.Enter, available, onCommand, haptic)
            RemoteKeyButton(RemoteKey.Right, available, onCommand, haptic)
        }
        RemoteKeyButton(RemoteKey.Down, available, onCommand, haptic)
    }
}

/** Touchpad gesture recognition stays in app; protocol encoding stays inside Samsung. */
@Composable
private fun Touchpad(
    onCommand: (TvCommand) -> Unit,
    haptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 240.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Television touchpad"
                    onClick(label = "Select with touchpad") {
                        haptic()
                        onCommand(TvCommand.PointerClick)
                        true
                    }
                }
                .pointerInput(onCommand) {
                    detectDragGestures(
                        onDragStart = { haptic() },
                        onDrag = { change, drag ->
                            change.consume()
                            onCommand(TvCommand.PointerMove(drag.x.toInt(), drag.y.toInt()))
                        },
                    )
                }
                .pointerInput(onCommand) {
                    detectTapGestures {
                        haptic()
                        onCommand(TvCommand.PointerClick)
                    }
                }
                .testTag(RemoteTestTags.TOUCHPAD),
        color = ColorTokens.surfaceElevated,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.remote_touchpad_hint), style = TypeTokens.body)
        }
    }
}

@Composable
private fun RemoteKeyButton(
    key: RemoteKey,
    available: Set<RemoteKey>,
    onCommand: (TvCommand) -> Unit,
    haptic: () -> Unit,
) {
    if (key !in available) return
    Button(
        onClick = {
            haptic()
            onCommand(TvCommand.Tap(key))
        },
        modifier =
            Modifier.defaultMinSize(
                    minWidth = SizeTokens.primaryControl,
                    minHeight = SizeTokens.primaryControl,
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
        ConnectionUi.Reconnecting -> stringResource(R.string.remote_reconnecting)
        is ConnectionUi.NeedsRepair -> stringResource(failure.message())
        is ConnectionUi.Unavailable -> stringResource(failure.message())
        ConnectionUi.Unsupported -> stringResource(R.string.remote_unsupported)
    }

@Composable
private fun ConnectionUi.statusGlyph(): String =
    when (this) {
        ConnectionUi.Ready -> "✓"
        ConnectionUi.Connecting,
        ConnectionUi.WaitingForApproval,
        ConnectionUi.Reconnecting -> "…"
        is ConnectionUi.NeedsRepair,
        is ConnectionUi.Unavailable -> "!"
        ConnectionUi.Unsupported -> "⊘"
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
        TvFailure.SecretsUnavailable -> R.string.pairing_failed_secrets_unavailable
        TvFailure.IdentityChanged -> R.string.remote_identity_changed
        else -> R.string.remote_unavailable
    }

@Preview(showBackground = true)
@Composable
private fun RemoteReadyPreview() {
    AppTTheme {
        RemoteScreen(
            state = RemoteUiState("Living Room TV", ConnectionUi.Ready, MINIMAL_REMOTE_KEYS),
            onCommand = {},
            onRetry = {},
        )
    }
}

private val HIGH_FREQUENCY_KEYS =
    listOf(RemoteKey.Back, RemoteKey.Home, RemoteKey.VolumeDown, RemoteKey.Mute, RemoteKey.VolumeUp)

/** Stable everyday-control order, filtered by live capability evidence before composition. */
val MINIMAL_REMOTE_KEYS =
    listOf(
        RemoteKey.VolumeUp,
        RemoteKey.VolumeDown,
        RemoteKey.Mute,
        RemoteKey.Up,
        RemoteKey.Left,
        RemoteKey.Enter,
        RemoteKey.Right,
        RemoteKey.Down,
        RemoteKey.Back,
        RemoteKey.Home,
    )
