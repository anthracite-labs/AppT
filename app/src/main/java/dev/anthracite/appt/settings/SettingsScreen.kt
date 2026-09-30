package dev.anthracite.appt.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import dev.anthracite.appt.R
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens

/** S05's functional Interaction and About sections; no later-slice destinations are shown. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onHaptics: (Boolean) -> Unit,
    onPhoneVolumeButtons: (Boolean) -> Unit,
    onNavigationMode: (NavigationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SpaceTokens.lg, vertical = SpaceTokens.md),
            verticalArrangement = Arrangement.spacedBy(SpaceTokens.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceTokens.md),
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = TypeTokens.title,
                    color = ColorTokens.contentPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                OutlinedButton(
                    onClick = onBack,
                    modifier =
                        Modifier.defaultSettingsTarget()
                            .testTag(SettingsTestTags.BACK),
                ) { Text(stringResource(R.string.settings_back)) }
            }
            Text(
                stringResource(R.string.settings_interaction),
                style = TypeTokens.title,
                color = ColorTokens.contentPrimary,
                modifier = Modifier.semantics { heading() },
            )
            PreferenceToggleRow(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                checked = state.interaction.hapticsEnabled,
                tag = SettingsTestTags.HAPTICS,
                onChange = onHaptics,
            )
            PreferenceToggleRow(
                title = stringResource(R.string.settings_phone_volume),
                summary = stringResource(R.string.settings_phone_volume_summary),
                checked = state.interaction.volumeButtonsControlTv,
                tag = SettingsTestTags.PHONE_VOLUME,
                onChange = onPhoneVolumeButtons,
            )
            Column(verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs)) {
                Text(
                    stringResource(R.string.settings_navigation_mode),
                    style = TypeTokens.body,
                    color = ColorTokens.contentPrimary,
                )
                if (state.pointerAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceTokens.sm)) {
                        NavigationChoice(
                            label = stringResource(R.string.settings_navigation_directional),
                            selected = state.effectiveNavigationMode == NavigationMode.Directional,
                            tag = SettingsTestTags.DIRECTIONAL,
                            onClick = { onNavigationMode(NavigationMode.Directional) },
                        )
                        NavigationChoice(
                            label = stringResource(R.string.settings_navigation_pointer),
                            selected = state.effectiveNavigationMode == NavigationMode.Pointer,
                            tag = SettingsTestTags.POINTER,
                            onClick = { onNavigationMode(NavigationMode.Pointer) },
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_navigation_directional),
                        style = TypeTokens.label,
                        color = ColorTokens.contentSecondary,
                        modifier = Modifier.testTag(SettingsTestTags.DIRECTIONAL_ONLY),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_about),
                style = TypeTokens.title,
                color = ColorTokens.contentPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = SpaceTokens.xs)
                        .testTag(SettingsTestTags.ABOUT),
                verticalArrangement = Arrangement.spacedBy(SpaceTokens.xs),
            ) {
                Text(
                    stringResource(R.string.settings_app_version),
                    style = TypeTokens.body,
                    color = ColorTokens.contentPrimary,
                )
                Text(
                    state.appVersion,
                    style = TypeTokens.label,
                    color = ColorTokens.contentSecondary,
                    modifier = Modifier.testTag(SettingsTestTags.VERSION),
                )
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = SizeTokens.settingsRow)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag(tag)
                .padding(horizontal = SpaceTokens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceTokens.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TypeTokens.body, color = ColorTokens.contentPrimary)
            Text(summary, style = TypeTokens.label, color = ColorTokens.contentSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavigationChoice(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.defaultSettingsTarget()
                .semantics { this.selected = selected }
                .testTag(tag),
    ) { Text(label, style = TypeTokens.label) }
}

private fun Modifier.defaultSettingsTarget(): Modifier =
    defaultMinSize(minWidth = SizeTokens.minimumTouchTarget, minHeight = SizeTokens.minimumTouchTarget)
