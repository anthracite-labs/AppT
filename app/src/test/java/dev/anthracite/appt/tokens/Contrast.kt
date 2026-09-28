package dev.anthracite.appt.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue

/**
 * WCAG 2.x contrast ratio, computed locally.
 *
 * This implements the published WCAG relative-luminance and contrast-ratio definitions directly
 * over the token values, so the token floor remains deterministic without device instrumentation.
 * Rendered Compose surfaces receive additional platform accessibility and contrast checks in S14.
 *
 * It checks the *token pairs*, not rendered pixels. Rendered-surface contrast verification belongs
 * to the accessibility slice (S14).
 */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
    val darker = min(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}

internal fun assertContrastAtLeast(floor: Double, foreground: Color, background: Color) {
    val ratio = contrastRatio(foreground, background)
    assertTrue(
        "contrast ${"%.2f".format(ratio)}:1 is below the ${floor}:1 floor " +
            "for $foreground on $background",
        ratio >= floor,
    )
}
