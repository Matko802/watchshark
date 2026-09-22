package com.darkk.youtube.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DownloadInfo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val duration: String,
    val quality: String,
    val fileUri: String,
    var progress: Float = 0f,
    var status: DownloadStatus = DownloadStatus.PENDING,
    val downloadId: String = System.currentTimeMillis().toString()
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

class DownloadDatabase(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dbFile = File(context.filesDir, "downloads.json")

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (dbFile.exists()) {
            try {
                _downloads.value = json.decodeFromString(dbFile.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveDownload(info: DownloadInfo) = withContext(Dispatchers.IO) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.videoId == info.videoId }
        if (index != -1) {
            current[index] = info
        } else {
            current.add(0, info)
        }
        _downloads.value = current
        saveToFile()
    }

    suspend fun updateProgress(videoId: String, progress: Float, status: DownloadStatus) = withContext(Dispatchers.IO) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            current[index] = current[index].copy(progress = progress, status = status)
            _downloads.value = current
            // Save frequently but maybe not every single byte to avoid I/O blocking
            if (progress == 1f || status == DownloadStatus.FAILED || status == DownloadStatus.PAUSED || status == DownloadStatus.COMPLETED) {
                saveToFile()
            }
        }
    }
    
    suspend fun removeDownload(videoId: String) = withContext(Dispatchers.IO) {
        val current = _downloads.value.toMutableList()
        current.removeAll { it.videoId == videoId }
        _downloads.value = current
        saveToFile()
    }

    private fun saveToFile() {
        try {
            dbFile.writeText(json.encodeToString(_downloads.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
