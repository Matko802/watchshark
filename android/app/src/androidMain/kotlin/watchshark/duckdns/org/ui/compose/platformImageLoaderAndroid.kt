package watchshark.duckdns.org.ui.compose

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

actual fun platformImageLoader(): SingletonImageLoader.Factory {
    return SingletonImageLoader.Factory { ctx ->
        ImageLoader.Builder(ctx)
            .crossfade(true)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
