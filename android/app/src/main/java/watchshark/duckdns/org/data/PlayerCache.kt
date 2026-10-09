package watchshark.duckdns.org.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File


@UnstableApi
object PlayerCache {
    private const val DIR = "media"
    private const val MAX_BYTES = 300L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        return cache ?: SimpleCache(
            File(context.cacheDir, DIR),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }


    fun mediaSource(context: Context, url: String, props: Map<String, String>): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(props)
        val cached = CacheDataSource.Factory()
            .setCache(get(context.applicationContext))
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return ProgressiveMediaSource.Factory(cached)
            .createMediaSource(MediaItem.fromUri(url))
    }

    fun release() {
        synchronized(this) {
            try {
                cache?.release()
            } catch (_: Exception) {
            }
            cache = null
        }
    }
}
