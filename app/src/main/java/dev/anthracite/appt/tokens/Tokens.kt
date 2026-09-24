package dev.anthracite.appt.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AppT design tokens.
 *
 * This is the design-token **module** in the codebase-design sense: a code/package-level module
 * inside `:app`, not a fourth Gradle module (docs/architecture/modules.md#shape).
 *
 * docs/architecture/presentation.md#design-tokens owns the categories and the binding floors. S01
 * creates the categories the walking skeleton needs — `color`, `type`, `space`, `size`, `motion` —
 * with their floors encoded as real values rather than as prose. Categories that no S01 surface
 * consumes (`shape`, `elevation`, `haptic`, `icon`, `brand`) are deliberately not invented here;
 * they arrive with the surfaces that need them.
 *
 * Binding floors carried by this file:
 * * text sizes are declared in `sp`, never `dp`, so they scale;
 * * every spacing step is a multiple of 4dp;
 * * [SizeTokens.minimumTouchTarget] is 48dp and [SizeTokens.primaryControl] is 56dp;
 * * every motion duration has a reduced-motion variant.
 *
 * Final pixel values are an implementation choice reviewed visually (presentation.md, "What this
 * file does not decide"); the roles and the floors are the architecture.
 */
object AppTTokens {
    val color: ColorTokens = ColorTokens
    val type: TypeTokens = TypeTokens
    val space: SpaceTokens = SpaceTokens
    val size: SizeTokens = SizeTokens
    val motion: MotionTokens = MotionTokens
}

/**
 * `color.*` — dark-first surfaces with content roles whose contrast floors are stated beside each
 * value so a later change cannot quietly drop below them.
 *
 * Contrast ratios below were computed from the WCAG 2.x relative-luminance formula for the exact
 * pairs listed. They are not produced by an automated Compose contrast checker: whether such
 * tooling covers Compose surfaces is an open provider-fact item in
 * docs/architecture/README.md#needs-validation, so S01 does not depend on one.
 */
@Immutable
object ColorTokens {
    // color.brand — restrained accent; used for the primary action container.
    val brandAccent: Color = Color(0xFF4FC3F7)
    val brandOnAccent: Color = Color(0xFF00202E)

    // color.surface — dark-first.
    //
    // Floor: 3:1 against adjacent non-text content. A dark-first design gets
    // its elevation from a subtle tone shift, which by itself cannot reach 3:1
    // against the surface beneath it, so the surface boundary is carried by
    // [surfaceOutline], which does meet the floor against both surfaces
    // (4.41:1 on `surface`, 3.87:1 on `surfaceElevated`). Tone alone is never
    // the only signal that a surface boundary exists.
    val surface: Color = Color(0xFF101418)
    val surfaceElevated: Color = Color(0xFF1B2127)
    val surfaceOutline: Color = Color(0xFF727D88)

    // color.content — floor: 4.5:1 for body text on its surface.
    // contentPrimary   #E7ECF2 on #101418 -> ~14.5:1
    // contentSecondary #B3BDC7 on #101418 -> ~ 8.6:1
    val contentPrimary: Color = Color(0xFFE7ECF2)
    val contentSecondary: Color = Color(0xFFB3BDC7)
    // Disabled content is exempt from the text contrast floor (WCAG 1.4.3
    // "Incidental"); it still clears 3:1 against the surface so a disabled
    // control remains perceivable.
    val contentDisabled: Color = Color(0xFF6B7681)

    // color.status — always paired with text or an icon, never colour alone.
    val statusReady: Color = Color(0xFF7BD88F)
    val statusAttention: Color = Color(0xFFFFCA6B)
    val statusUnavailable: Color = Color(0xFFFF8A80)

    // color.feedback — same contrast floors as content.
    val feedbackError: Color = Color(0xFFFF8A80)
    val feedbackWarning: Color = Color(0xFFFFCA6B)
    val feedbackSuccess: Color = Color(0xFF7BD88F)
    val feedbackInfo: Color = Color(0xFF8FD3FF)
}

/**
 * `type.*` — every size is `sp`, so system font scaling applies. No fixed `dp` text size exists
 * anywhere in the token set.
 */
@Immutable
object TypeTokens {
    val display: TextStyle =
        TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val title: TextStyle =
        TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val body: TextStyle =
        TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val label: TextStyle =
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    /** Smallest text size the token set permits, in scalable units. */
    val smallestScalableSize: TextUnit = label.fontSize
}

/** `space.*` — a 4dp-based scale. Every step is a multiple of [grid]. */
@Immutable
object SpaceTokens {
    val grid: Dp = 4.dp

    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    val all: List<Dp> = listOf(none, xs, sm, md, lg, xl, xxl)

    /** Compact-height windows shrink vertical spacing by one step. */
    fun compactHeight(step: Dp): Dp {
        val index = all.indexOf(step)
        return if (index > 0) all[index - 1] else step
    }
}

/** `size.*` — carries the binding 48dp / 56dp interactive floors. */
@Immutable
object SizeTokens {
    /** Binding floor: no interactive target is smaller than this. */
    val minimumTouchTarget: Dp = 48.dp

    /** Primary controls exceed the floor where the architecture requires it. */
    val primaryControl: Dp = 56.dp

    val iconSmall: Dp = 20.dp
    val iconMedium: Dp = 24.dp

    /** Centred single-column surfaces cap at a comfortable reading width. */
    val readableContentMaxWidth: Dp = 640.dp
}

/**
 * `motion.*` — every duration has a reduced-motion variant, and composables read the variant rather
 * than hardcoding a number (presentation.md composition rule 4).
 */
@Immutable
object MotionTokens {
    /** Reduced motion means no travel and no timed reveal: duration is zero. */
    const val REDUCED_DURATION_MILLIS: Int = 0

    const val STATE_CHANGE_MILLIS: Int = 120
    const val SHEET_MILLIS: Int = 220
    const val SURFACE_TRANSITION_MILLIS: Int = 300
    const val MASCOT_MILLIS: Int = 600

    /** Returns [duration], or the reduced-motion variant when motion is reduced. */
    fun durationFor(duration: Int, reducedMotion: Boolean): Int =
        if (reducedMotion) REDUCED_DURATION_MILLIS else duration
}

/**
 * The current motion duration scale, where `0f` means "remove animations".
 *
 * presentation.md's accessibility contract says motion tokens read `LocalMotionDurationScale`.
 * Compose itself models this as a [androidx.compose.ui.MotionDurationScale] coroutine-context
 * element rather than a CompositionLocal, so AppT owns the CompositionLocal of that name and
 * `MainActivity` supplies it from the platform setting. Tests provide it directly, which is what
 * makes the reduced-motion assertion possible without a device setting.
 */
val LocalMotionDurationScale: ProvidableCompositionLocal<Float> = staticCompositionLocalOf { 1f }

/** True when the platform (or a test) asks for animations to be removed. */
@Composable
@ReadOnlyComposable
fun isReducedMotion(): Boolean = LocalMotionDurationScale.current == 0f

/**
 * Resolves a `motion.*` duration against the current motion duration scale.
 *
 * Composables call this instead of naming a number, so reduced motion is honored by construction.
 */
@Composable
@ReadOnlyComposable
fun motionDuration(durationMillis: Int): Int =
    MotionTokens.durationFor(durationMillis, isReducedMotion())
