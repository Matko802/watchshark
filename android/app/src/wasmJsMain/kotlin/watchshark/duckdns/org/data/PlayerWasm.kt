package watchshark.duckdns.org.data

import androidx.compose.ui.geometry.Rect
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import watchshark.duckdns.org.ui.Context
import kotlin.math.roundToInt

internal object WebVideoStage {
    private class Entry(
        val player: WebSharkPlayer,
        val rect: Rect,
        val density: Float,
        val seq: Long,
    )

    var nextId = 0L
    private var seq = 0L
    private val entries = mutableMapOf<Long, Entry>()

    fun placeSurface(id: Long, player: WebSharkPlayer?, rect: Rect, density: Float) {
        if (player == null || rect.width <= 0f || rect.height <= 0f) {
            entries.remove(id)
        } else {
            entries[id] = Entry(player, rect, density, ++seq)
        }
        reapply()
    }

    fun disposeSurface(id: Long) {
        entries.remove(id)
        reapply()
    }

    private var shownEl: HTMLElement? = null

    private fun reapply() {
        val best = entries.values.maxByOrNull { it.seq }
        if (best == null) {
            shownEl?.style?.display = "none"
            shownEl = null
            return
        }
        val el = best.player.el
        if (el !== shownEl) {
            shownEl?.style?.display = "none"
            el.style.display = "block"
            shownEl = el
        }
        el.style.left = ((best.rect.left / best.density).roundToInt()).toString() + "px"
        el.style.top = ((best.rect.top / best.density).roundToInt()).toString() + "px"
        el.style.width = ((best.rect.width / best.density).roundToInt()).toString() + "px"
        el.style.height = ((best.rect.height / best.density).roundToInt()).toString() + "px"
    }

    private fun container(): HTMLElement {
        val existing = document.getElementById("ws-video-stage")
        if (existing != null) return existing as HTMLElement
        val el = document.createElement("div") as HTMLElement
        el.id = "ws-video-stage"
        el.style.position = "absolute"
        el.style.top = "0px"
        el.style.left = "0px"
        el.style.width = "100%"
        el.style.height = "100%"
        el.style.setProperty("overflow", "hidden")
        el.style.setProperty("pointer-events", "none")
        document.body?.insertBefore(el, document.body?.firstChild)
        return el
    }

    fun host(video: HTMLVideoElement) {
        val container = container()
        container.appendChild(video)
        video.style.position = "absolute"
        video.style.display = "none"
        video.style.setProperty("pointer-events", "none")
        video.style.setProperty("object-fit", "contain")
    }

    fun preinit() {
        container()
    }
}

internal class WebSharkPlayer internal constructor() : PPlayer {
    internal val el = (document.createElement("video") as HTMLVideoElement).also { v ->
        v.controls = false
        v.setAttribute("playsinline", "")
        v.preload = "auto"
    }

    private val items = mutableListOf<String>()
    private var index = 0
    private var pendingSeekMs = 0L
    override var listener: PPlayerListener? = null

    init {
        WebVideoStage.host(el)
        el.addEventListener("loadedmetadata", { _ -> consumePendingSeek(); fireState() })
        el.addEventListener("canplay", { _ -> tryAutoplay(); fireState() })
        el.addEventListener("playing", { _ -> fireState() })
        el.addEventListener("pause", { _ -> fireState() })
        el.addEventListener("play", { _ -> fireState() })
        el.addEventListener("waiting", { _ -> fireState() })
        el.addEventListener("ended", { _ -> onEndedInternal() })
        el.addEventListener("error", { _ -> fireState() })
    }

    private fun consumePendingSeek() {
        val p = pendingSeekMs
        if (p > 0) {
            try {
                el.currentTime = p / 1000.0
            } catch (_: Exception) {
            }
        }
        pendingSeekMs = 0
    }

    private fun fireState() {
        listener?.onStateChanged(index.coerceAtLeast(0), ready, buffering, playWhenReady)
    }

    private fun fireTransition(i: Int) {
        listener?.onTransition(i)
    }

    override val itemCount: Int get() = items.size
    override val currentIndex: Int get() = index.coerceAtLeast(0)
    override val playing: Boolean get() = (!el.paused && !el.ended)
    override var playWhenReady: Boolean = false
        set(value) {
            field = value
            if (value && ready) {
                try {
                    el.play()
                } catch (_: Exception) {
                }
            } else if (!value) {
                try {
                    el.pause()
                } catch (_: Exception) {
                }
            }
        }
    override val positionMs: Long
        get() = (el.currentTime * 1000).toLong().coerceAtLeast(0)
    override val durationMs: Long
        get() {
            val d = el.duration
            return if (d.isNaN() || d <= 0) 0L else (d * 1000).toLong()
        }
    override val bufferedMs: Long
        get() {
            return try {
                val b = el.buffered
                if (b != null && b.length > 0) (b.end(b.length - 1) * 1000).toLong() else 0L
            } catch (_: Exception) {
                0L
            }
        }
    override val ready: Boolean
        get() = el.readyState.toInt() >= 3 || el.ended
    override val buffering: Boolean
        get() = el.readyState.toInt() == 2
    override var volume: Float
        get() = el.volume.toFloat()
        set(value) {
            try {
                el.volume = value.coerceIn(0f, 1f).toDouble()
            } catch (_: Exception) {
            }
        }
    override var repeatOne: Boolean = false

    override fun play() {
        playWhenReady = true
    }

    override fun pause() {
        playWhenReady = false
    }

    override fun stop() {
        items.clear()
        index = 0
        pendingSeekMs = 0
        try {
            el.pause()
            el.removeAttribute("src")
            el.load()
        } catch (_: Exception) {
        }
        fireState()
    }

    override fun seekTo(ms: Long) {
        try {
            el.currentTime = ms.coerceAtLeast(0) / 1000.0
        } catch (_: Exception) {
        }
    }

    override fun seekToItem(target: Int, ms: Long) {
        if (target < 0 || target >= items.size) return
        index = target
        load(items[index], ms.coerceAtLeast(0), playWhenReady)
        fireTransition(index)
    }

    override fun switchTo(url: String) {
        items.clear()
        items.add(url)
        index = 0
        val keep = ready && durationMs > 0
        val pos = if (keep) positionMs else 0L
        val resume = if (keep) playWhenReady else true
        load(url, pos, resume)
        fireTransition(0)
    }

    override fun append(url: String) {
        items.add(url)
        if (items.size == 1) {
            load(url, 0L, playWhenReady)
        }
    }

    override fun replace(idx: Int, url: String) {
        if (idx < 0 || idx >= items.size) return
        items[idx] = url
        if (idx == currentIndex) {
            load(url, if (ready) positionMs else 0L, playWhenReady)
        }
    }

    override fun clear() {
        stop()
    }

    private fun tryAutoplay() {
        if (playWhenReady) {
            try {
                el.play()
            } catch (_: Exception) {
            }
        }
    }

    private fun load(url: String, posMs: Long, resume: Boolean) {
        pendingSeekMs = posMs.coerceAtLeast(0)
        try {
            el.pause()
        } catch (_: Exception) {
        }
        el.src = url
        if (resume) playWhenReady = true
        try {
            el.load()
        } catch (_: Exception) {
        }
    }

    private fun onEndedInternal() {
        fireState()
        if (repeatOne) {
            try {
                el.currentTime = 0.0
                el.play()
            } catch (_: Exception) {
            }
            return
        }
        val next = index + 1
        if (next < items.size) {
            seekToItem(next, 0L)
        }
    }
}

actual object PlayerManager {

    private var player: WebSharkPlayer? = null

    actual fun get(ctx: Context): PPlayer {
        return player ?: WebSharkPlayer().also { player = it }
    }

    actual fun build(): PPlayer = WebSharkPlayer()

    actual fun pauseForBackground() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
    }

    actual fun stopAndClear() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
    }

    actual fun release() {
    }
}
