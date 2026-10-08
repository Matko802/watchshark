package watchshark.duckdns.org.ui.compose.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Central motion tokens (M3 Expressive-inspired).
 * Physics springs for spatial movement, tweens for fades.
 * Keeps every screen on the same feel without depending on the
 * experimental MotionScheme API (works on any BOM).
 */
object AppMotion {
    val fastSpatial = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val defaultSpatial = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun fadeFast() = fadeIn(tween(150, easing = FastOutSlowInEasing))
    fun fadeOutFast() = fadeOut(tween(150, easing = FastOutSlowInEasing))

    // Shared-axis style nav transitions (YouTube-like lateral motion).
    val screenEnter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
    val screenExit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 12 }
    val screenPopEnter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 8 }
    val screenPopExit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it / 12 }

    val listEnter = fadeIn(tween(250, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 10 }
    val listExit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 12 }

    val popIn = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.6f) +
        fadeIn(tween(150))
    val popOut = scaleOut(tween(150), targetScale = 0.6f) + fadeOut(tween(150))
}
