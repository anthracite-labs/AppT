package dev.anthracite.appt.welcome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.LocalMotionDurationScale
import dev.anthracite.appt.tokens.SizeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Welcome's product shape and accessibility contracts.
 *
 * These run on Robolectric so the accessibility assertions execute on every CI run rather than only
 * when a device is attached
 * (docs/architecture/presentation.md#accessibility-contracts-and-verification-hooks).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WelcomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun setWelcome(
        motionDurationScale: Float = 1f,
        fontScale: Float = 1f,
        onFindMyTv: () -> Unit = {},
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalMotionDurationScale provides motionDurationScale,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                AppTTheme { WelcomeScreen(onFindMyTv = onFindMyTv) }
            }
        }
    }

    private fun clickableNodeCount(): Int =
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
            .size

    /**
     * presentation.md's accessibility contract: "Targets are at least 48dp".
     *
     * Asserted over each clickable node's *touch* bounds, which is what the platform actually
     * dispatches against, and compared to the token that owns the floor rather than to a literal.
     */
    private fun assertEveryClickableMeetsTheTouchTargetFloor() {
        val nodes =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .fetchSemanticsNodes()
        assertTrue("expected at least one interactive control", nodes.isNotEmpty())

        val floorPx = with(composeRule.density) { SizeTokens.minimumTouchTarget.toPx() }
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue(
                "touch width ${bounds.width}px is below the " +
                    "${SizeTokens.minimumTouchTarget} floor (${floorPx}px)",
                bounds.width + 0.5f >= floorPx,
            )
            assertTrue(
                "touch height ${bounds.height}px is below the " +
                    "${SizeTokens.minimumTouchTarget} floor (${floorPx}px)",
                bounds.height + 0.5f >= floorPx,
            )
        }
    }

    // --- product shape (ui-ux.md#onboarding) --------------------------------

    @Test
    fun `shows the core value proposition`() {
        setWelcome()
        composeRule.onNodeWithTag(WelcomeTestTags.VALUE_PROPOSITION).assertIsDisplayed()
    }

    @Test
    fun `shows a privacy and local-control reassurance`() {
        setWelcome()
        composeRule.onNodeWithTag(WelcomeTestTags.REASSURANCE).assertIsDisplayed()
    }

    @Test
    fun `offers exactly one primary action`() {
        setWelcome()
        assertEquals(
            "Welcome must offer exactly one primary action (ui-ux.md#onboarding)",
            1,
            clickableNodeCount(),
        )
    }

    @Test
    fun `the primary action is Find my TV and reports the press`() {
        var pressed = 0
        setWelcome(onFindMyTv = { pressed++ })
        composeRule.onNodeWithText("Find my TV").performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun `the surface is static text, not a multi-page carousel`() {
        setWelcome()
        // A feature carousel would expose a horizontal scroll range on a pager.
        val horizontallyScrollable =
            composeRule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
                )
                .fetchSemanticsNodes()
        assertEquals(
            "Welcome must not be a multi-page feature carousel",
            0,
            horizontallyScrollable.size,
        )
    }

    // --- accessibility contracts --------------------------------------------

    @Test
    fun `the primary action has a click action and a TalkBack label`() {
        setWelcome()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).assertHasClickAction()
        composeRule.onNodeWithText("Find my TV").assertIsDisplayed()
    }

    @Test
    fun `every interactive control meets the 48dp touch target floor`() {
        setWelcome()
        assertEveryClickableMeetsTheTouchTargetFloor()
    }

    @Test
    fun `the decorative brand mark carries no meaning in semantics`() {
        setWelcome()
        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.TestTag, WelcomeTestTags.MASCOT)
            )
            .fetchSemanticsNodes()
            .forEach { node ->
                assertNull(
                    "the decorative brand mark must expose no text to TalkBack",
                    node.config.getOrNull(SemanticsProperties.Text),
                )
                assertNull(
                    "the decorative brand mark must expose no content description",
                    node.config.getOrNull(SemanticsProperties.ContentDescription),
                )
            }
    }

    @Test
    fun `stays usable at 200 percent font scale`() {
        setWelcome(fontScale = 2f)
        // The surface scrolls, so nothing is dropped or made unreachable when
        // text doubles in size.
        composeRule.onNodeWithTag(WelcomeTestTags.VALUE_PROPOSITION).assertExists()
        composeRule.onNodeWithTag(WelcomeTestTags.REASSURANCE).assertExists()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).assertExists()
        assertEveryClickableMeetsTheTouchTargetFloor()
    }

    @Test
    fun `renders and stays interactive when motion is reduced`() {
        setWelcome(motionDurationScale = 0f)
        // Reduced motion removes the brand mark's timed reveal; the surface and
        // its single action are unaffected.
        composeRule.onNodeWithTag(WelcomeTestTags.MASCOT).assertExists()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).assertHasClickAction()
        assertEquals(1, clickableNodeCount())
    }
}
