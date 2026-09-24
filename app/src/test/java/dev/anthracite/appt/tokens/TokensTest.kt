package dev.anthracite.appt.tokens

import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The token floors from docs/architecture/presentation.md#design-tokens, asserted as executable
 * rules rather than left as prose.
 */
class TokensTest {

    @Test
    fun `exposes the five S01 token categories`() {
        // The categories S01's acceptance criteria name.
        assertTrue(AppTTokens.color === ColorTokens)
        assertTrue(AppTTokens.type === TypeTokens)
        assertTrue(AppTTokens.space === SpaceTokens)
        assertTrue(AppTTokens.size === SizeTokens)
        assertTrue(AppTTokens.motion === MotionTokens)
    }

    @Test
    fun `minimum interactive target is the 48dp floor`() {
        assertEquals(48.dp, SizeTokens.minimumTouchTarget)
    }

    @Test
    fun `primary controls exceed the minimum floor at 56dp`() {
        assertEquals(56.dp, SizeTokens.primaryControl)
        assertTrue(SizeTokens.primaryControl >= SizeTokens.minimumTouchTarget)
    }

    @Test
    fun `every spacing step is a multiple of the 4dp grid`() {
        assertEquals(4.dp, SpaceTokens.grid)
        SpaceTokens.all.forEach { step ->
            val steps = step.value / SpaceTokens.grid.value
            assertEquals(
                "spacing step $step is not a multiple of the 4dp grid",
                steps,
                Math.round(steps).toFloat(),
            )
        }
    }

    @Test
    fun `compact height shrinks a spacing step by exactly one`() {
        assertEquals(SpaceTokens.md, SpaceTokens.compactHeight(SpaceTokens.lg))
        // The smallest step has nowhere to shrink to and stays put.
        assertEquals(SpaceTokens.none, SpaceTokens.compactHeight(SpaceTokens.none))
    }

    @Test
    fun `every type role is declared in scalable units`() {
        listOf(TypeTokens.display, TypeTokens.title, TypeTokens.body, TypeTokens.label).forEach {
            style ->
            assertEquals(
                "text size $style must be declared in scalable sp, not dp",
                TextUnitType.Sp,
                style.fontSize.type,
            )
            assertEquals(
                "line height $style must be declared in scalable sp, not dp",
                TextUnitType.Sp,
                style.lineHeight.type,
            )
        }
    }

    @Test
    fun `every motion role has a reduced-motion variant that removes the animation`() {
        val roles =
            listOf(
                MotionTokens.STATE_CHANGE_MILLIS,
                MotionTokens.SHEET_MILLIS,
                MotionTokens.SURFACE_TRANSITION_MILLIS,
                MotionTokens.MASCOT_MILLIS,
            )
        roles.forEach { role ->
            assertTrue("motion role $role must have a positive normal duration", role > 0)
            assertEquals(
                MotionTokens.REDUCED_DURATION_MILLIS,
                MotionTokens.durationFor(role, reducedMotion = true),
            )
            assertEquals(role, MotionTokens.durationFor(role, reducedMotion = false))
        }
    }

    @Test
    fun `body text meets the 4-point-5 to 1 contrast floor on the dark surface`() {
        assertContrastAtLeast(4.5, ColorTokens.contentPrimary, ColorTokens.surface)
        assertContrastAtLeast(4.5, ColorTokens.contentSecondary, ColorTokens.surface)
        assertContrastAtLeast(4.5, ColorTokens.contentPrimary, ColorTokens.surfaceElevated)
        assertContrastAtLeast(4.5, ColorTokens.contentSecondary, ColorTokens.surfaceElevated)
    }

    @Test
    fun `the primary action label meets the contrast floor on the brand accent`() {
        assertContrastAtLeast(4.5, ColorTokens.brandOnAccent, ColorTokens.brandAccent)
    }

    @Test
    fun `the surface outline meets the 3 to 1 non-text floor against both surfaces`() {
        // Dark-first elevation is a subtle tone shift, so the outline is what
        // carries the surface boundary at the non-text contrast floor.
        assertContrastAtLeast(3.0, ColorTokens.surfaceOutline, ColorTokens.surface)
        assertContrastAtLeast(3.0, ColorTokens.surfaceOutline, ColorTokens.surfaceElevated)
    }

    @Test
    fun `disabled content stays perceivable at the non-text floor`() {
        assertContrastAtLeast(3.0, ColorTokens.contentDisabled, ColorTokens.surface)
    }

    @Test
    fun `status colours meet the contrast floor on the dark surface`() {
        listOf(
                ColorTokens.statusReady,
                ColorTokens.statusAttention,
                ColorTokens.statusUnavailable,
                ColorTokens.feedbackError,
                ColorTokens.feedbackWarning,
                ColorTokens.feedbackSuccess,
                ColorTokens.feedbackInfo,
            )
            .forEach { assertContrastAtLeast(4.5, it, ColorTokens.surface) }
    }
}
