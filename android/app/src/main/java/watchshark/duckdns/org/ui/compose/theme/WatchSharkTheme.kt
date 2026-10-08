package watchshark.duckdns.org.ui.compose.theme

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

// Fallback YouTube-like neutrals when dynamic color is unavailable.
// Dynamic color is preferred (user choice) for M3 Expressive.
private val FallbackDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA8A8A8),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2B2B2B),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFDDDDDD),
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerHighest = Color(0xFF1D1D1D),
    error = Color(0xFFF2B8B5),
)

// Pure-black AMOLED dark: the pre-rewrite look. All surfaces stay
// black so feeds and bars melt into the display.
private val AmoledDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA8A8A8),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFDDDDDD),
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainer = Color.Black,
    surfaceContainerHighest = Color(0xFF111111),
    error = Color(0xFFF2B8B5),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7E7),
    onPrimaryContainer = Color(0xFF0F0F0F),
    secondary = Color(0xFF606060),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7E7),
    onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFF404040),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainerHighest = Color(0xFFE9E9E9),
    error = Color(0xFFB3261E),
)

/**
 * M3 Expressive theme entry.
 * - Uses dynamic color on API 31+ (Material You / Expressive).
 * - Falls back to YouTube-like monochrome otherwise.
 * - amoled=true forces the old pure-black AMOLED look in dark mode.
 * - Expressive shapes/motion come from material3 1.3+ defaults
 *   (64dp NavigationBar, pill indicators, spring motion).
 */
@Composable
fun WatchSharkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        darkTheme && amoled -> AmoledDark
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(
        colorScheme = colorScheme,
        // Use default M3 Expressive type + shapes from the library.
        // Custom brand typography can be added here later.
        content = content,
    )
}
