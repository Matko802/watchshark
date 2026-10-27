package watchshark.duckdns.org.ui.compose.player

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import watchshark.duckdns.org.data.PPlayer

@Composable
actual fun VideoSurfacePlatform(
    player: PPlayer?,
    modifier: Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = (player as? watchshark.duckdns.org.data.ExoSharkPlayer)?.exo
        },
        modifier = modifier,
    )
}
