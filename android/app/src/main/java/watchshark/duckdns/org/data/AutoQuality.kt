package watchshark.duckdns.org.data
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
    fun rungIndex(key: String?): Int =
        LADDER.indexOfFirst { it.key == key }.coerceAtLeast(0)
    fun rungKey(idx: Int): String = LADDER[idx.coerceIn(LADDER.indices)].key
    fun rungCount(): Int = LADDER.size
    fun readyKeys(vid: Video): List<String> {
        val keys = mutableListOf<String>()
        for (r in LADDER) {
            if (r.key == "src") {
                if (!vid.src.isNullOrBlank()) keys.add("src")
            } else if (vid.renditions?.containsKey(r.key) == true) {
                keys.add(r.key)
            }
        }
        return keys
    }
    fun readyUrl(vid: Video, key: String): String? {
        if (key == "src") return ApiClient.fullUrl(vid.src)
        return vid.renditions?.get(key)?.let { ApiClient.fullUrl(it) }
    }
    fun pickReadyKey(vid: Video): String {
        val ready = readyKeys(vid)
        if (ready.isEmpty()) return "360p"
        val est = estimateBps()
        if (est <= 0) {
            return ready.filter { it != "src" }.maxByOrNull { rungIndex(it) } ?: "src"
        }
        val budget = (est * 0.8).toLong()
        return ready.filter { LADDER[rungIndex(it)].needBps <= budget }
            .maxByOrNull { rungIndex(it) }
            ?: ready.minByOrNull { rungIndex(it) }!!
    }
    fun lowerReadyKey(vid: Video, curKey: String?): String? {
        val cur = rungIndex(curKey)
        return readyKeys(vid).filter { rungIndex(it) < cur }.maxByOrNull { rungIndex(it) }
    }
    /** Throttle gate for any switch; stamps the clock when it opens. */
    fun tryBeginSwitch(gapMs: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSwitchMs < gapMs) return false
        lastSwitchMs = now
        return true
    }
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
    fun stepDownSingle(exo: ExoPlayer, vid: Video, curKey: String?): String? {
        val want = lowerReadyKey(vid, curKey) ?: return null
        val url = readyUrl(vid, want) ?: return null
        if (!tryBeginSwitch(DOWNGRADE_GAP_MS)) return null
        switchSingle(exo, url)
        return want
    }
}