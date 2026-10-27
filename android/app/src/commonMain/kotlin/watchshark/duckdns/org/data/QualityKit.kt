package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.compose.player.dynRenditionUrl

object QualityKit {
    val AUTO = "Auto"
    val SOURCE = "Source"
    val LADDER = listOf("720p", "480p", "360p")

    fun options(video: Video): List<String> {
        val out = mutableListOf(AUTO)
        for (r in LADDER) {
            val hasMap = video.renditions?.containsKey(r) == true
            val hasDyn = dynRenditionUrl(video.src, r) != null
            if (hasMap || hasDyn) out.add(r)
        }
        out.add(SOURCE)
        return out
    }

    fun url(video: Video, selected: String): String? {
        if (selected != AUTO && selected != SOURCE) {
            return video.renditions?.get(selected)
                ?: dynRenditionUrl(video.src, selected)?.let { ApiClient.fullUrl(it) }
                ?: ApiClient.fullUrl(video.src)
        }
        if (selected == AUTO) {
            val key = AutoQuality.pickReadyKey(video)
            AutoQuality.readyUrl(video, key)?.let { return it }
        }
        return video.renditions?.get("720p")
            ?: video.renditions?.values?.firstOrNull()?.let { ApiClient.fullUrl(it) }
            ?: ApiClient.fullUrl(video.src)
    }

    fun describe(video: Video): String {
        val ready = AutoQuality.readyKeys(video)
        if (ready.isEmpty()) return "No renditions yet"
        return "Available: " + ready.joinToString(", ")
    }
}
