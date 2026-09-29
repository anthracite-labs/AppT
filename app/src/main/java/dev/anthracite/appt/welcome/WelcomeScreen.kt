package dev.anthracite.appt.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import dev.anthracite.appt.R
import dev.anthracite.appt.diagnostics.FlightRecorder
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.ColorTokens
import dev.anthracite.appt.tokens.MotionTokens
import dev.anthracite.appt.tokens.SizeTokens
import dev.anthracite.appt.tokens.SpaceTokens
import dev.anthracite.appt.tokens.TypeTokens
import dev.anthracite.appt.tokens.motionDuration

/**
 * The Welcome surface.
 *
 * ui-ux.md#onboarding settles the shape: one concise screen carrying the core value proposition, a
 * short privacy/local-control reassurance, restrained brand personality, and exactly one primary
 * action. Not a multi-page feature carousel.
 *
 * The composable is stateless (presentation.md composition rule 4): it takes callbacks and reads
 * tokens, and owns no state, ViewModel or data source. presentation.md's eventual `WelcomeUiState`
 * carries `permissionAcknowledged` and `rememberedCount`, both of which are fed by persistence that
 * S01 does not introduce; rather than fake those inputs, S01 renders the first-run shape only and
 * the state type arrives with the data that fills it.
 *
 * @param onFindMyTv invoked by the single primary action. S01's navigation graph contains only
 *   Welcome, so the host supplies a no-op; S02 is where this becomes the local-network explanation.
 */
@Composable
fun WelcomeScreen(onFindMyTv: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = ColorTokens.surface) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    // Scrolling is what keeps large font scales from clipping or
                    // stranding the primary action off-screen.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SpaceTokens.lg, vertical = SpaceTokens.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceTokens.md, Alignment.CenterVertically),
        ) {
            WelcomeMascot()

            Text(
                text = stringResource(R.string.welcome_value_proposition),
                style = TypeTokens.display,
                color = ColorTokens.contentPrimary,
                modifier =
                    Modifier.widthIn(max = SizeTokens.readableContentMaxWidth)
                        .testTag(WelcomeTestTags.VALUE_PROPOSITION),
            )

            Text(
                text = stringResource(R.string.welcome_privacy_reassurance),
                style = TypeTokens.body,
                color = ColorTokens.contentSecondary,
                modifier =
                    Modifier.widthIn(max = SizeTokens.readableContentMaxWidth)
                        .testTag(WelcomeTestTags.REASSURANCE),
            )

            // The one primary action. Sizing comes from size.* tokens, never
            // from a literal: 56dp for a primary control, which also satisfies
            // the 48dp interactive floor.
            Button(
                onClick = onFindMyTv,
                modifier =
                    Modifier.fillMaxWidth()
                        .widthIn(max = SizeTokens.readableContentMaxWidth)
                        .defaultMinSize(
                            minWidth = SizeTokens.minimumTouchTarget,
                            minHeight = SizeTokens.primaryControl,
                        )
                        .testTag(WelcomeTestTags.PRIMARY_ACTION),
            ) {
                Text(
                    text = stringResource(R.string.welcome_primary_action),
                    style = TypeTokens.label,
                )
            }

            DebugReportExport()
        }
    }
}

/**
 * TEMPORARY S04 diagnostic (Issue #91): debug builds surface the saved flight-recorder report
 * here after relaunch; the recorder is inert off the diagnostic build, so release renders
 * nothing extra and behaves exactly as before. Its own composable to keep this screen small.
 */
@Composable
private fun DebugReportExport() {
    // Only when there is something to export: a fresh diagnostic build shows exactly the one
    // primary action (ui-ux.md#onboarding), and the control appears after a report was saved.
    if (!FlightRecorder.enabled) return
    if (FlightRecorder.reportFile() == null) return
    val context = LocalContext.current
    // The chooser label resolves through the composition, so it follows the configuration.
    val exportLabel = stringResource(R.string.debug_export_report)
    TextButton(
        onClick = {
            FlightRecorder.exportIntent(context)?.let { intent ->
                context.startActivity(Intent.createChooser(intent, exportLabel))
            }
        },
        // The temporary diagnostic affordance stays out of the accessibility semantics tree, so
        // Welcome keeps exactly one primary action for assistive tech and for the ui-ux count;
        // the visible, touchable label remains for the physical tester.
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        Text(text = stringResource(R.string.debug_export_report), style = TypeTokens.label)
    }
}

/**
 * The decorative brand mark.
 *
 * Its own composable rather than inline in [WelcomeScreen]: it is self-contained presentation
 * with its own animation state, and inlining it pushed the screen composable past the LongMethod
 * threshold. Nothing about behaviour changes — the node keeps its test tag, keeps its motion
 * token, and stays hidden from the semantics tree.
 *
 * Restrained brand personality. It is decorative: it carries no meaning, so it is hidden from the
 * semantics tree entirely (presentation.md: decorative nodes are marked decorative).
 */
@Composable
private fun WelcomeMascot(modifier: Modifier = Modifier) {
    val mascotAlpha by
        animateFloatAsState(
            targetValue = 1f,
            // Timing comes from the motion token, resolved against the
            // reduced-motion setting. No duration literal appears here.
            animationSpec = tween(durationMillis = motionDuration(MotionTokens.MASCOT_MILLIS)),
            label = "welcomeMascotFade",
        )
    Text(
        text = stringResource(R.string.welcome_mascot_glyph),
        style = TypeTokens.display,
        color = ColorTokens.brandAccent,
        modifier =
            modifier.alpha(mascotAlpha).testTag(WelcomeTestTags.MASCOT).clearAndSetSemantics {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    AppTTheme { WelcomeScreen(onFindMyTv = {}) }
}
