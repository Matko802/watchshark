package watchshark.duckdns.org.ui.compose

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade

actual fun platformImageLoader(): SingletonImageLoader.Factory {
    return SingletonImageLoader.Factory { ctx ->
        ImageLoader.Builder(ctx)
            .crossfade(true)
            .build()
    }
}
