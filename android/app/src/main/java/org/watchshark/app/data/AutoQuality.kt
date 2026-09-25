package org.watchshark.app.data

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.BandwidthMeter

/**
 * Adaptive quality for the progressive renditions (360p / 480p / 720p /
 * Source). ExoPlayer's shared bandwidth meter measures the real connection
 * speed while video flows; Auto picks the highest rung that fits with
 * headroom, steps down fast on stalls, and steps up when the estimate
 * stays comfortably above the next rung. Manual overrides bypass it.
 *
 * Server encodes (video + 96k audio): 360p ~0.6 Mbps, 480p ~1.1 Mbps,
 * 720p ~2.6 Mbps; Source is the original file (unknown, often huge).
 */
object AutoQuality {
    data class Rung(val key: String, val needBps: Long)

    private val LADDER = listOf(
        Rung("360p", 0),
        Rung("480p", 1_500_000),
        Rung("720p", 4_000_000),
        Rung("src", 12_000_000)
    )

    /** Min time between upgrades (avoids yo-yoing). */
    const val UPGRADE_GAP_MS = 10_000L
    /** Min time between downgrades (still fast enough to unstick). */
    const val DOWNGRADE_GAP_MS = 5_000L
    /** Don't bother upgrading this close to the end. */
    private const val TAIL_MS = 5_000L

    @Volatile
    var meter: BandwidthMeter? = null
    @Volatile
    private var lastSwitchMs = 0L

    fun bind(m: BandwidthMeter) {
        if (meter == null) meter = m
    }

    fun estimateBps(): Long =
        try {
            meter?.bitrateEstimate ?: Format.NO_VALUE.toLong()
        } catch (_: Exception) {
            Format.NO_VALUE.toLong()
        }

    /** Highest rung fitting in 80% of the estimate (headroom). Unknown → 360p. */
    fun pickKey(): String {
        val est = estimateBps()
        if (est <= 0) return "360p"
        val budget = (est * 0.8).toLong()
        var key = "360p"
        for (r in LADDER) if (r.needBps <= budget) key = r.key
        return key
    }

    fun rungIndex(key: String?): Int =
        LADDER.indexOfFirst { it.key == key }.coerceAtLeast(0)

    fun rungKey(idx: Int): String = LADDER[idx.coerceIn(LADDER.indices)].key

    fun rungCount(): Int = LADDER.size

    /** Throttle gate for any switch; stamps the clock when it opens. */
    fun tryBeginSwitch(gapMs: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSwitchMs < gapMs) return false
        lastSwitchMs = now
        return true
    }

    /** Resolve a rung to an absolute playable URL. */
    fun urlFor(vid: Video, key: String): String? {
        if (key == "src") return ApiClient.fullUrl(vid.src)
        vid.renditions?.get(key)?.let { return ApiClient.fullUrl(it) }
        return dynRendition(vid, key)?.let { ApiClient.fullUrl(it) }
    }

    /** Dynamic rendition URL (generates on first request server-side). */
    private fun dynRendition(vid: Video, res: String): String? {
        val stem = Regex("""/v/(.+)\.[a-z0-9]+$""", RegexOption.IGNORE_CASE)
            .find(vid.src)?.groupValues?.get(1)
            ?.removeSuffix("-720p")?.removeSuffix("-480p")?.removeSuffix("-360p")
            ?: return null
        return "/v/$stem-$res.webm"
    }

    /** Swap a single-item player's source, keeping position and play state. */
    fun switchSingle(exo: ExoPlayer, url: String) {
        val pos = exo.currentPosition.coerceAtLeast(0)
        val resume = exo.playWhenReady
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.seekTo(pos)
        exo.playWhenReady = resume
    }

    /**
     * Upgrade check for single-item players. Returns the new key, or null
     * when no switch happened (same/better rung, throttled, or near the end).
     */
    fun maybeUpgradeSingle(exo: ExoPlayer, vid: Video, curKey: String?): String? {
        val want = pickKey()
        if (rungIndex(want) <= rungIndex(curKey)) return null
        val dur = exo.duration
        val pos = exo.currentPosition.coerceAtLeast(0)
        if (dur != C.TIME_UNSET && dur > 0 && dur - pos < TAIL_MS) return null
        val url = urlFor(vid, want) ?: return null
        if (!tryBeginSwitch(UPGRADE_GAP_MS)) return null
        switchSingle(exo, url)
        return want
    }

    /** One rung down for single-item players. Returns the new key or null. */
    fun stepDownSingle(exo: ExoPlayer, vid: Video, curKey: String?): String? {
        val idx = rungIndex(curKey)
        if (idx <= 0) return null
        val want = rungKey(idx - 1)
        val url = urlFor(vid, want) ?: return null
        if (!tryBeginSwitch(DOWNGRADE_GAP_MS)) return null
        switchSingle(exo, url)
        return want
    }
}
