package dev.anthracite.appt.tokens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Binds the AppT tokens to Material 3 so Compose's own defaults can never introduce an untokenised
 * colour or text size.
 *
 * ui-ux.md: Material foundations are used where useful, with the dark remote surface as the primary
 * design reference.
 */
@Composable
fun AppTTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = ColorTokens.brandAccent,
                onPrimary = ColorTokens.brandOnAccent,
                background = ColorTokens.surface,
                onBackground = ColorTokens.contentPrimary,
                surface = ColorTokens.surface,
                onSurface = ColorTokens.contentPrimary,
                surfaceVariant = ColorTokens.surfaceElevated,
                onSurfaceVariant = ColorTokens.contentSecondary,
                error = ColorTokens.feedbackError,
            ),
        typography =
            Typography(
                displaySmall = TypeTokens.display,
                titleLarge = TypeTokens.title,
                bodyLarge = TypeTokens.body,
                labelLarge = TypeTokens.label,
            ),
        content = content,
    )
}
