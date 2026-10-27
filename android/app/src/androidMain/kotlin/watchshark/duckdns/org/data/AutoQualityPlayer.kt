package watchshark.duckdns.org.data

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

fun AutoQuality.bind(m: BandwidthMeter) {
    estimateProvider = {
        try {
            m.bitrateEstimate.takeIf { it != Format.NO_VALUE.toLong() && it > 0 } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }
}

fun AutoQuality.bindDefault(appCtx: android.content.Context) {
    try {
        val meter = DefaultBandwidthMeter.getSingletonInstance(appCtx)
        bind(meter)
    } catch (_: Exception) {
    }
}

fun AutoQuality.switchSingle(exo: ExoPlayer, url: String) {
    val pos = exo.currentPosition.coerceAtLeast(0)
    val resume = exo.playWhenReady
    exo.setMediaItem(MediaItem.fromUri(url))
    exo.prepare()
    exo.seekTo(pos)
    exo.playWhenReady = resume
}

fun AutoQuality.maybeUpgradeSingle(exo: ExoPlayer, vid: Video, curKey: String?): String? {
    val want = pickReadyKey(vid)
    if (rungIndex(want) <= rungIndex(curKey)) return null
    val dur = exo.duration
    val pos = exo.currentPosition.coerceAtLeast(0)
    if (dur != C.TIME_UNSET && dur > 0 && dur - pos < TAIL_MS) return null
    val url = readyUrl(vid, want) ?: return null
    if (!tryBeginSwitch(UPGRADE_GAP_MS)) return null
    switchSingle(exo, url)
    return want
}

fun AutoQuality.stepDownSingle(exo: ExoPlayer, vid: Video, curKey: String?): String? {
    val want = lowerReadyKey(vid, curKey) ?: return null
    val url = readyUrl(vid, want) ?: return null
    if (!tryBeginSwitch(DOWNGRADE_GAP_MS)) return null
    switchSingle(exo, url)
    return want
}

private const val TAIL_MS = 5_000L
