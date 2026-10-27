package watchshark.duckdns.org.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import watchshark.duckdns.org.ui.Uikit

actual fun dynamicSchemeOrNull(darkTheme: Boolean): ColorScheme? {
    return try {
        if (darkTheme) dynamicDarkColorScheme(Uikit.ctx) else dynamicLightColorScheme(Uikit.ctx)
    } catch (_: Throwable) {
        null
    }
}
