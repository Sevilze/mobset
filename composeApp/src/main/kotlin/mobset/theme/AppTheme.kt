package mobset.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
// Material 3 expressive color scheme implementation
// Using built-in Compose Material 3 color utilities

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> expressiveColorScheme(darkTheme, seedColor)
        }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun expressiveColorScheme(dark: Boolean, seed: Color) =
    // Material 3 expressive color scheme with enhanced vibrancy and expressiveness
    // Based on the seed color, create a more vibrant and expressive palette
    if (dark) {
        darkColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = seed.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = adjustHue(seed, 60f),
            onSecondary = Color.White,
            secondaryContainer = adjustHue(seed, 60f).copy(alpha = 0.3f),
            onSecondaryContainer = Color.White,
            tertiary = adjustHue(seed, 120f),
            onTertiary = Color.White,
            tertiaryContainer = adjustHue(seed, 120f).copy(alpha = 0.3f),
            onTertiaryContainer = Color.White,
            error = Color(0xFFCF6679),
            onError = Color.Black,
            errorContainer = Color(0xFFCF6679).copy(alpha = 0.3f),
            onErrorContainer = Color.White,
            background = Color(0xFF121212),
            onBackground = Color.White,
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2D2D2D),
            onSurfaceVariant = Color(0xFFE0E0E0),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFF404040),
            scrim = Color.Black,
            inverseSurface = Color(0xFFF5F5F5),
            inverseOnSurface = Color(0xFF1C1C1C),
            inversePrimary = seed.copy(alpha = 0.8f)
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = seed.copy(alpha = 0.2f),
            onPrimaryContainer = darken(seed, 0.3f),
            secondary = adjustHue(seed, 60f),
            onSecondary = Color.White,
            secondaryContainer = adjustHue(seed, 60f).copy(alpha = 0.2f),
            onSecondaryContainer = darken(adjustHue(seed, 60f), 0.3f),
            tertiary = adjustHue(seed, 120f),
            onTertiary = Color.White,
            tertiaryContainer = adjustHue(seed, 120f).copy(alpha = 0.2f),
            onTertiaryContainer = darken(adjustHue(seed, 120f), 0.3f),
            error = Color(0xFFB00020),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF3F3F3),
            onSurfaceVariant = Color(0xFF424242),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFFCACACA),
            scrim = Color.Black,
            inverseSurface = Color(0xFF313131),
            inverseOnSurface = Color(0xFFF4F4F4),
            inversePrimary = lighten(seed, 0.3f)
        )
    }

// Helper functions for color manipulation
private fun adjustHue(color: Color, degrees: Float): Color {
    // Simple hue adjustment by rotating RGB values
    val red = color.red
    val green = color.green
    val blue = color.blue

    // Rotate colors to simulate hue shift
    return when {
        degrees >= 60f && degrees < 120f -> Color(green, blue, red, color.alpha)
        degrees >= 120f -> Color(blue, red, green, color.alpha)
        else -> Color(
            red * 0.8f + green * 0.2f,
            green * 0.8f + blue * 0.2f,
            blue * 0.8f + red * 0.2f,
            color.alpha
        )
    }
}

private fun darken(color: Color, factor: Float): Color = Color(
    red = (color.red * (1f - factor)).coerceIn(0f, 1f),
    green = (color.green * (1f - factor)).coerceIn(0f, 1f),
    blue = (color.blue * (1f - factor)).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun lighten(color: Color, factor: Float): Color = Color(
    red = (color.red + (1f - color.red) * factor).coerceIn(0f, 1f),
    green = (color.green + (1f - color.green) * factor).coerceIn(0f, 1f),
    blue = (color.blue + (1f - color.blue) * factor).coerceIn(0f, 1f),
    alpha = color.alpha
)
