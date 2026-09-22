package com.darkk.youtube.innertube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Browse / Search response models ──────────────────────────────────────────

@Serializable
data class SearchResponse(
    val contents: SearchContents? = null,
    val onResponseReceivedCommands: List<SearchResponseCommand>? = null
)

@Serializable
data class SearchResponseCommand(
    val appendContinuationItemsAction: SearchAppendContinuationItemsAction? = null
)

@Serializable
data class SearchAppendContinuationItemsAction(
    val continuationItems: List<ItemSectionContent>? = null
)

@Serializable
data class SearchContents(
    val twoColumnSearchResultsRenderer: TwoColumnSearchResults? = null
)

@Serializable
data class TwoColumnSearchResults(
    val primaryContents: PrimaryContents? = null
)

@Serializable
data class PrimaryContents(
    val sectionListRenderer: SectionListRenderer? = null
)

@Serializable
data class SectionListRenderer(
    val contents: List<SectionContent>? = null
)

@Serializable
data class SectionContent(
    val itemSectionRenderer: ItemSectionRenderer? = null,
    val continuationItemRenderer: ContinuationItemRenderer? = null
)

@Serializable
data class ItemSectionRenderer(
    val contents: List<ItemSectionContent>? = null
)

@Serializable
data class ItemSectionContent(
    val videoRenderer: VideoRenderer? = null,
    val continuationItemRenderer: ContinuationItemRenderer? = null
)

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null
)

@Serializable
data class ContinuationEndpoint(
    val continuationCommand: ContinuationCommand? = null
)

@Serializable
data class ContinuationCommand(
    val token: String? = null
)

@Serializable
data class VideoRenderer(
    val videoId: String? = null,
    val title: Runs? = null,
    val thumbnail: Thumbnails? = null,
    val ownerText: Runs? = null,
    val viewCountText: SimpleText? = null,
    val publishedTimeText: SimpleText? = null,
    val lengthText: SimpleText? = null,
    val channelThumbnailSupportedRenderers: ChannelThumbnailSupportedRenderers? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

@Serializable
data class NavigationEndpoint(
    val commandMetadata: CommandMetadata? = null
)

@Serializable
data class CommandMetadata(
    val webCommandMetadata: WebCommandMetadata? = null
)

@Serializable
data class WebCommandMetadata(
    val url: String? = null
)

@Serializable
data class ChannelThumbnailSupportedRenderers(
    val channelThumbnailWithLinkRenderer: ChannelThumbnailWithLinkRenderer? = null
)

@Serializable
data class ChannelThumbnailWithLinkRenderer(
    val thumbnail: Thumbnails? = null
)

@Serializable
data class Runs(
    val runs: List<Run>? = null
) {
    fun text() = runs?.joinToString("") { it.text ?: "" } ?: ""
}

@Serializable
data class SearchSuggestion(
    val query: String
)

@Serializable
data class ReleaseItem(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val isExplicit: Boolean
)

@Serializable
data class Run(
    val text: String? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

@Serializable
data class SimpleText(
    val simpleText: String? = null
)

@Serializable
data class Thumbnails(
    val thumbnails: List<ThumbnailItem>? = null
) {
    fun bestQuality(): String? = thumbnails?.maxByOrNull { (it.width ?: 0) }?.url
    fun mqDefault(): String? = thumbnails?.let {
        val videoId = it.firstOrNull()?.url?.substringAfter("vi/")?.substringBefore("/") ?: ""
        if (videoId.isNotEmpty()) "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
        else it.lastOrNull()?.url
    }
}

@Serializable
data class ThumbnailItem(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

// ─── Player response (stream URLs) ────────────────────────────────────────────
// Mirrors AirBeats' PlayerResponse.kt

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null
    )

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format>? = null,
        val expiresInSeconds: String? = null
    ) {
        @Serializable
        data class Format(
            val itag: Int = 0,
            val url: String? = null,
            @SerialName("signatureCipher") val signatureCipher: String? = null,
            val mimeType: String? = null,
            val bitrate: Int? = null,
            val width: Int? = null,
            val height: Int? = null,
            val quality: String? = null,
            val qualityLabel: String? = null,
            val fps: Int? = null,
            val contentLength: Long? = null,
            val approxDurationMs: String? = null
        ) {
            val isVideo: Boolean get() = width != null && height != null
            val isAudio: Boolean get() = width == null
            val qualityScore: Int get() = height ?: 0
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null
    )
}

// ─── Home / Browse response ────────────────────────────────────────────────────

@Serializable
data class BrowseResponse(
    val contents: BrowseContents? = null,
    val responseContext: ResponseContext? = null,
    val onResponseReceivedActions: List<ResponseAction>? = null
)

@Serializable
data class ResponseContext(
    val visitorData: String? = null
)

@Serializable
data class ResponseAction(
    val appendContinuationItemsAction: AppendContinuationItemsAction? = null
)

@Serializable
data class AppendContinuationItemsAction(
    val continuationItems: List<RichGridContent>? = null
)

@Serializable
data class BrowseContents(
    val twoColumnBrowseResultsRenderer: TwoColumnBrowseResults? = null,
    val singleColumnBrowseResultsRenderer: SingleColumnBrowseResults? = null
)

@Serializable
data class TwoColumnBrowseResults(
    val tabs: List<Tab>? = null
)

@Serializable
data class SingleColumnBrowseResults(
    val tabs: List<Tab>? = null
)

@Serializable
data class Tab(
    val tabRenderer: TabRenderer? = null
)

@Serializable
data class TabRenderer(
    val content: TabContent? = null
)

@Serializable
data class TabContent(
    val richGridRenderer: RichGridRenderer? = null,
    val sectionListRenderer: SectionListRenderer? = null
)

@Serializable
data class RichGridRenderer(
    val contents: List<RichGridContent>? = null
)

@Serializable
data class RichGridContent(
    val richItemRenderer: RichItemRenderer? = null,
    val richSectionRenderer: RichSectionRenderer? = null,
    val continuationItemRenderer: ContinuationItemRenderer? = null
)

@Serializable
data class RichItemRenderer(
    val content: RichItemContent? = null
)

@Serializable
data class RichItemContent(
    val videoRenderer: VideoRenderer? = null,
    val reelItemRenderer: ReelItemRenderer? = null,
    val continuationItemRenderer: ContinuationItemRenderer? = null
)

@Serializable
data class RichSectionRenderer(
    val content: RichSectionContent? = null
)

@Serializable
data class RichSectionContent(
    val richShelfRenderer: RichShelfRenderer? = null
)

@Serializable
data class RichShelfRenderer(
    val title: Runs? = null,
    val contents: List<RichItemRenderer>? = null
)

@Serializable
data class ReelItemRenderer(
    val videoId: String? = null,
    val headline: SimpleText? = null,
    val thumbnail: Thumbnails? = null,
    val viewCountText: SimpleText? = null
)

@Serializable
data class SubscribedChannel(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val handle: String? = null
)

