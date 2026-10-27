package watchshark.duckdns.org.data

interface PPlayer {
    val itemCount: Int
    val currentIndex: Int
    val playing: Boolean
    var playWhenReady: Boolean
    val positionMs: Long
    val durationMs: Long
    val bufferedMs: Long
    val ready: Boolean
    val buffering: Boolean
    var volume: Float
    var repeatOne: Boolean
    var listener: PPlayerListener?

    fun play()
    fun pause()
    fun stop()
    fun seekTo(ms: Long)
    fun seekToItem(index: Int, ms: Long)
    fun switchTo(url: String)
    fun append(url: String)
    fun replace(index: Int, url: String)
    fun clear()
}

interface PPlayerListener {
    fun onTransition(index: Int)
    fun onStateChanged(index: Int, ready: Boolean, buffering: Boolean, playWhenReady: Boolean)
}
