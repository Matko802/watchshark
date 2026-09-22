package com.github.libretube.repo

import com.github.libretube.api.WatchSharkBackend
import com.github.libretube.api.obj.Playlist
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.api.obj.Subscription
import com.github.libretube.api.obj.WatchHistoryEntry
import com.github.libretube.api.obj.WatchHistoryEntryMetadata
import com.github.libretube.db.obj.PlaylistBookmark
import com.github.libretube.db.obj.SubscriptionGroup
import com.github.libretube.enums.WatchHistoryStatus

/**
 * WatchShark account backend. Delegates everything local (playlists, history,
 * bookmarks, groups) to [LocalUserDataRepository] and syncs auth plus
 * follows with the WatchShark server.
 */
class WatchSharkUserDataRepository(
    private val local: UserDataRepository = LocalUserDataRepository()
) : UserDataRepository by local {

    override suspend fun login(username: String, password: String): String {
        val (id, name) = WatchSharkBackend.login(username, password)
        local.login(name, username)
        return "ws:$id"
    }

    override suspend fun register(username: String, password: String): String {
        throw Exception("Register on the WatchShark website, then log in here")
    }

    override suspend fun logout() {
        try {
            WatchSharkBackend.logout()
        } catch (_: Exception) {
        }
        local.logout()
    }

    override suspend fun subscribe(channelId: String, name: String, uploaderAvatar: String?, verified: Boolean) {
        local.subscribe(channelId, name, uploaderAvatar, verified)
        try {
            WatchSharkBackend.followByName(channelId.removePrefix("@"), true)
        } catch (_: Exception) {
        }
    }

    override suspend fun unsubscribe(channelId: String) {
        local.unsubscribe(channelId)
        try {
            WatchSharkBackend.followByName(channelId.removePrefix("@"), false)
        } catch (_: Exception) {
        }
    }

    override suspend fun isSubscribed(channelId: String): Boolean? {
        return local.isSubscribed(channelId)
    }

    override suspend fun getSubscriptions(): List<Subscription> = local.getSubscriptions()

    override suspend fun getSubscriptionChannelIds(): List<String> = local.getSubscriptionChannelIds()
}
