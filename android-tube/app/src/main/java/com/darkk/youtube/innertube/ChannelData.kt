package com.darkk.youtube.innertube

data class ChannelData(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val subscribers: String,
    val videoCount: String,
    val description: String,
    val isVerified: Boolean,
    val handle: String = "",
    val videos: List<VideoItem> = emptyList()
)
