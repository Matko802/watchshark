package com.darkk.youtube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class DownloadManager(
    private val context: Context,
    val database: DownloadDatabase
) {
    private val client = OkHttpClient.Builder().build()
    private val downloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager = NotificationManagerCompat.from(context)
    
    // Simple state flow to observe active downloads count
    private val _activeDownloads = MutableStateFlow(0)
    val activeDownloads: StateFlow<Int> = _activeDownloads.asStateFlow()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_channel",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YouTube Video Downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(info: DownloadInfo, progress: Int, max: Int, isCompleted: Boolean, isFailed: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val builder = NotificationCompat.Builder(context, "download_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(info.title)
            .setOnlyAlertOnce(true)

        if (isCompleted) {
            builder.setContentText("Download complete")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setProgress(0, 0, false)
        } else if (isFailed) {
            builder.setContentText("Download failed")
                .setProgress(0, 0, false)
        } else {
            builder.setContentText("Downloading... $progress%")
                .setProgress(max, progress, false)
        }

        notificationManager.notify(info.videoId.hashCode(), builder.build())
    }

    fun startDownload(info: DownloadInfo) {
        if (downloadJobs.containsKey(info.videoId)) return // Already downloading

        val job = scope.launch {
            try {
                database.saveDownload(info.copy(status = DownloadStatus.DOWNLOADING))
                _activeDownloads.value = downloadJobs.size + 1
                
                updateNotification(info, 0, 100, isCompleted = false, isFailed = false)
                
                // Fetch real streaming URL
                val playerData = com.darkk.youtube.innertube.YouTubeApi.getPlayerData(info.videoId).getOrNull()
                if (playerData == null) {
                    database.updateProgress(info.videoId, info.progress, DownloadStatus.FAILED)
                    updateNotification(info, (info.progress * 100).toInt(), 100, isCompleted = false, isFailed = true)
                    return@launch
                }
                
                val isAudio = info.quality == "audio"
                val url = if (isAudio) {
                    playerData.qualities.firstOrNull { it.audioUrl != null }?.audioUrl
                        ?: playerData.qualities.firstOrNull()?.videoUrl
                } else {
                    playerData.qualities.firstOrNull { it.label.contains(info.quality) }?.videoUrl
                        ?: playerData.qualities.firstOrNull()?.videoUrl
                }
                
                if (url == null) {
                    database.updateProgress(info.videoId, info.progress, DownloadStatus.FAILED)
                    updateNotification(info, (info.progress * 100).toInt(), 100, isCompleted = false, isFailed = true)
                    return@launch
                }

                val success = downloadFile(url, info)
                
                if (success) {
                    database.updateProgress(info.videoId, 1f, DownloadStatus.COMPLETED)
                    updateNotification(info, 100, 100, isCompleted = true, isFailed = false)
                } else {
                    database.updateProgress(info.videoId, info.progress, DownloadStatus.FAILED)
                    updateNotification(info, (info.progress * 100).toInt(), 100, isCompleted = false, isFailed = true)
                }
            } catch (e: CancellationException) {
                database.updateProgress(info.videoId, info.progress, DownloadStatus.PAUSED)
                updateNotification(info, (info.progress * 100).toInt(), 100, isCompleted = false, isFailed = true)
            } catch (e: Exception) {
                e.printStackTrace()
                database.updateProgress(info.videoId, info.progress, DownloadStatus.FAILED)
                updateNotification(info, (info.progress * 100).toInt(), 100, isCompleted = false, isFailed = true)
            } finally {
                downloadJobs.remove(info.videoId)
                _activeDownloads.value = downloadJobs.size
            }
        }
        downloadJobs[info.videoId] = job
    }

    fun pauseDownload(videoId: String) {
        downloadJobs[videoId]?.cancel()
    }
    
    fun resumeDownload(info: DownloadInfo) {
        startDownload(info) // Resume logic would need range headers, but keeping it simple for now or restarting
    }

    fun cancelDownload(videoId: String) {
        downloadJobs[videoId]?.cancel()
        scope.launch {
            database.removeDownload(videoId)
        }
    }

    private suspend fun downloadFile(url: String, info: DownloadInfo): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        val isAudio = info.quality == "audio"
        val ext = if (isAudio) "mp3" else "mp4"
        
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    println("DownloadManager: HTTP failed with code ${response.code}")
                    return@withContext false
                }
                
                val body = response.body
                if (body == null) {
                    println("DownloadManager: HTTP body is null")
                    return@withContext false
                }
                val totalBytes = body.contentLength()
                
                val fileName = "${info.title.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")}_${info.quality}.$ext"
                
                val outputStream = getOutputStream(fileName, isAudio)
                if (outputStream == null) {
                    println("DownloadManager: outputStream is null for $fileName")
                    return@withContext false
                }
                
                body.byteStream().use { input ->
                    outputStream.use { output ->
                        copyTo(input, output, totalBytes) { progress ->
                            launch {
                                database.updateProgress(info.videoId, progress, DownloadStatus.DOWNLOADING)
                                updateNotification(info, (progress * 100).toInt(), 100, isCompleted = false, isFailed = false)
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
                false
            }
        }
    }
    
    private suspend fun copyTo(
        input: InputStream, 
        output: OutputStream, 
        totalBytes: Long, 
        onProgress: suspend (Float) -> Unit
    ) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var lastUpdate = System.currentTimeMillis()
        
        var bytes = input.read(buffer)
        while (bytes >= 0) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException()
            }
            output.write(buffer, 0, bytes)
            bytesCopied += bytes
            
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 500 && totalBytes > 0) {
                onProgress(bytesCopied.toFloat() / totalBytes.toFloat())
                lastUpdate = now
            }
            bytes = input.read(buffer)
        }
        if (totalBytes > 0) {
            onProgress(1f)
        }
    }

    private fun getOutputStream(fileName: String, isAudio: Boolean): OutputStream? {
        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        val directory = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/YouTube")
            }
            val collectionUri = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collectionUri, contentValues)
            return uri?.let { resolver.openOutputStream(it) }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(directory), "YouTube")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            return file.outputStream()
        }
    }
}
