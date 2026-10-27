package watchshark.duckdns.org.data

import kotlin.concurrent.Volatile
import watchshark.duckdns.org.ui.Uikit

object AutoQuality {
    data class Rung(val key: String, val needBps: Long)
    private val LADDER = listOf(
        Rung("360p", 0),
        Rung("480p", 1_500_000),
        Rung("720p", 4_000_000),
        Rung("src", 12_000_000),
    )

    const val UPGRADE_GAP_MS = 10_000L

    const val DOWNGRADE_GAP_MS = 5_000L

    private const val TAIL_MS = 5_000L

    internal var estimateProvider: () -> Long = { -1L }
    @Volatile
    private var lastSwitchMs = 0L

    fun estimateBps(): Long =
        try {
            estimateProvider().takeIf { it > 0 } ?: -1L
        } catch (_: Exception) {
            -1L
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

    fun tryBeginSwitch(gapMs: Long): Boolean {
        val now = Uikit.monoMs()
        if (now - lastSwitchMs < gapMs) return false
        lastSwitchMs = now
        return true
    }
}
