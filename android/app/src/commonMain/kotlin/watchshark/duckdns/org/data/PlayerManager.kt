package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

expect object PlayerManager {
    fun get(ctx: Context): PPlayer
    fun build(): PPlayer
    fun pauseForBackground()
    fun stopAndClear()
    fun release()
}
