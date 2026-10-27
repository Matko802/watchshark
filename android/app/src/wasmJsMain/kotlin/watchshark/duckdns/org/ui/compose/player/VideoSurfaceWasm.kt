package watchshark.duckdns.org.ui.compose.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import watchshark.duckdns.org.data.PPlayer
import watchshark.duckdns.org.data.WebSharkPlayer
import watchshark.duckdns.org.data.WebVideoStage

@Composable
actual fun VideoSurfacePlatform(
    player: PPlayer?,
    modifier: Modifier,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density
    val id = remember { WebVideoStage.nextId++ }
    LaunchedEffect(player, rect, density) {
        WebVideoStage.placeSurface(id, player as? WebSharkPlayer, rect, density)
    }
    DisposableEffect(Unit) {
        onDispose { WebVideoStage.disposeSurface(id) }
    }
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (player != null) {
                    drawRect(Color.Black, blendMode = BlendMode.Clear)
                }
            }
            .onGloballyPositioned { rect = it.boundsInWindow() },
    )
}
