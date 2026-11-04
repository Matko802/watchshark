package watchshark.duckdns.org.ui.compose.theme

import androidx.compose.animation.core.CubicBezierEasing
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
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun fadeFast() = fadeIn(tween(150, easing = FastOutSlowInEasing))
    fun fadeOutFast() = fadeOut(tween(150, easing = FastOutSlowInEasing))

    private val emphasizeDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val emphasizeAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val screenEnter = fadeIn(tween(260, easing = emphasizeDecelerate)) +
        slideInHorizontally(tween(300, easing = emphasizeDecelerate)) { it / 6 }
    val screenExit = fadeOut(tween(160, easing = emphasizeAccelerate)) +
        slideOutHorizontally(tween(200, easing = emphasizeAccelerate)) { -it / 10 }
    val screenPopEnter = fadeIn(tween(260, easing = emphasizeDecelerate)) +
        slideInHorizontally(tween(300, easing = emphasizeDecelerate)) { -it / 6 }
    val screenPopExit = fadeOut(tween(160, easing = emphasizeAccelerate)) +
        slideOutHorizontally(tween(200, easing = emphasizeAccelerate)) { it / 10 }

    val listEnter = fadeIn(tween(250, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 10 }
    val listExit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 12 }

    val popIn = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.6f) +
        fadeIn(tween(150))
    val popOut = scaleOut(tween(150), targetScale = 0.6f) + fadeOut(tween(150))
}
