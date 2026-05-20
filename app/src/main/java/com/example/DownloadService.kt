package com.example

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

enum class DownloadState { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class DownloadInfo(
    val id: Long,
    val url: String,
    val fileName: String,
    var progress: Float = 0f,
    var state: DownloadState = DownloadState.PENDING,
    var bytesDownloaded: Long = 0L,
    var bytesTotal: Long = 0L,
    var speed: Long = 0L,
    var error: String? = null
)

object DownloadEngine {
    val activeDownloads = MutableStateFlow<Map<Long, DownloadInfo>>(emptyMap())
    private val jobs = ConcurrentHashMap<Long, Job>()
    val client = OkHttpClient.Builder().build()
    var wifiOnly = false
    var concurrentLimit = 4
    private var downloadIdCounter = 1000L

    fun enqueue(context: Context, url: String, fileName: String, id: Long = downloadIdCounter++): Long {
        val info = DownloadInfo(id, url, fileName)
        updateInfo(info)
        
        val startIntent = Intent(context, DownloadService::class.java).apply {
            action = "START"
            putExtra("id", id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }
        return id
    }

    fun pause(id: Long) {
        jobs[id]?.cancel()
        jobs.remove(id)
        activeDownloads.value[id]?.let { 
            it.state = DownloadState.PAUSED
            it.speed = 0
            updateInfo(it)
        }
        DownloadService.updateNotification(id)
    }

    fun resume(context: Context, id: Long) {
        val info = activeDownloads.value[id] ?: return
        if (info.state == DownloadState.PAUSED || info.state == DownloadState.FAILED) {
            info.state = DownloadState.PENDING
            updateInfo(info)
            val intent = Intent(context, DownloadService::class.java).apply {
                action = "START"
                putExtra("id", id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
        jobs.remove(id)
        activeDownloads.value[id]?.let {
            it.state = DownloadState.CANCELLED
            updateInfo(it)
        }
        DownloadService.cancelNotification(id)
    }

    fun updateInfo(info: DownloadInfo) {
        activeDownloads.value = activeDownloads.value.toMutableMap().apply { put(info.id, info) }
    }
    
    fun setJob(id: Long, job: Job) {
        jobs[id] = job
    }
    
    fun removeJob(id: Long) {
        jobs.remove(id)
    }
    
    fun clearFinished() {
        val temp = activeDownloads.value.toMutableMap()
        temp.entries.removeIf { it.value.state == DownloadState.COMPLETED || it.value.state == DownloadState.CANCELLED }
        activeDownloads.value = temp
    }
}

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        const val CHANNEL_ID = "downloads_channel"
        private var instance: DownloadService? = null
        
        fun updateNotification(id: Long) {
            instance?.showNotification(id)
        }
        fun cancelNotification(id: Long) {
            instance?.notificationManager?.cancel(id.toInt())
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val id = intent?.getLongExtra("id", -1L) ?: -1L
        
        if (id == -1L) return START_NOT_STICKY

        when (action) {
            "START" -> startDownloadJob(id)
            "PAUSE" -> DownloadEngine.pause(id)
            "RESUME" -> DownloadEngine.resume(this, id)
            "CANCEL" -> DownloadEngine.cancel(id)
        }
        return START_STICKY
    }

    private fun startDownloadJob(id: Long) {
        val info = DownloadEngine.activeDownloads.value[id] ?: return
        
        // Wifi check
        if (DownloadEngine.wifiOnly && !isWifiConnected()) {
            info.state = DownloadState.PAUSED
            info.error = "Waiting for Wi-Fi"
            DownloadEngine.updateInfo(info)
            showNotification(id)
            return
        }
        
        info.state = DownloadState.DOWNLOADING
        DownloadEngine.updateInfo(info)
        // Make this foreground so OS doesn't kill it
        startForeground(id.toInt(), createNotification(info))
        
        val job = scope.launch {
            try {
                processChunkedDownload(info)
            } catch (e: CancellationException) {
                // Handled in Engine
            } catch (e: Exception) {
                info.state = DownloadState.FAILED
                info.error = e.localizedMessage
                info.speed = 0L
                DownloadEngine.updateInfo(info)
                showNotification(id)
            } finally {
                DownloadEngine.removeJob(id)
            }
        }
        DownloadEngine.setJob(id, job)
    }
    
    private suspend fun processChunkedDownload(info: DownloadInfo) {
        val client = DownloadEngine.client
        // 1. Get File Size and Accept-Ranges
        val headReq = Request.Builder().url(info.url).head().build()
        val response = client.newCall(headReq).execute()
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
        val acceptRanges = response.header("Accept-Ranges") == "bytes"
        response.close()

        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, info.fileName)

        info.bytesTotal = contentLength
        if (info.bytesTotal <= 0) info.bytesTotal = 1L // avoid /0

        if (contentLength > 0 && acceptRanges) {
            // Chunked logic
            val chunks = 4
            val chunkSize = contentLength / chunks
            
            // Pre-allocate
            val raf = RandomAccessFile(destFile, "rw")
            raf.setLength(contentLength)
            raf.close()
            
            val downloadedBytes = AtomicLong(0L)
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            coroutineScope {
                val jobs = (0 until chunks).map { i ->
                    launch(Dispatchers.IO) {
                        val start = i * chunkSize
                        val end = if (i == chunks - 1) contentLength - 1 else (start + chunkSize - 1)
                        // Note: For true resume, we would track each chunk's progress.
                        // Here we just restart the whole file for simplicity if not using a DB for chunks.
                        
                        val req = Request.Builder().url(info.url).header("Range", "bytes=$start-$end").build()
                        val res = client.newCall(req).execute()
                        res.body?.byteStream()?.use { input ->
                            val file = RandomAccessFile(destFile, "rw")
                            file.seek(start)
                            
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    yield()
                                    file.write(buffer, 0, read)
                                    downloadedBytes.addAndGet(read.toLong())
                                
                                val now = System.currentTimeMillis()
                                if (now - lastTime > 800) {
                                    val currentD = downloadedBytes.get()
                                    info.bytesDownloaded = currentD
                                    info.progress = (currentD.toFloat() / info.bytesTotal).coerceIn(0f, 1f)
                                    val timeDiff = now - lastTime
                                    if (timeDiff > 0) info.speed = ((currentD - lastBytes) * 1000L) / timeDiff
                                    
                                    lastBytes = currentD
                                    lastTime = now
                                    DownloadEngine.updateInfo(info.copy())
                                    updateNotification(info.id)
                                }
                            }
                            file.close()
                        }
                    }
                }
                jobs.forEach { it.join() }
            }
            info.bytesDownloaded = contentLength
            info.progress = 1.0f
        } else {
            // Single stream fallback
            val req = Request.Builder().url(info.url).build()
            val res = client.newCall(req).execute()
            res.body?.byteStream()?.use { input ->
                destFile.outputStream().use { output ->
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L
                    var downloaded = 0L
                    
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        yield()
                        output.write(buffer, 0, read)
                        downloaded += read
                        
                        val now = System.currentTimeMillis()
                        if (now - lastTime > 800) {
                            info.bytesDownloaded = downloaded
                            info.progress = if (contentLength > 0) (downloaded.toFloat() / info.bytesTotal).coerceIn(0f, 1f) else 0f
                            val timeDiff = now - lastTime
                            if (timeDiff > 0) info.speed = ((downloaded - lastBytes) * 1000L) / timeDiff
                            
                            lastBytes = downloaded
                            lastTime = now
                            DownloadEngine.updateInfo(info.copy())
                            updateNotification(info.id)
                        }
                    }
                }
            }
        }
        
        info.state = DownloadState.COMPLETED
        info.speed = 0L
        info.progress = 1f
        DownloadEngine.updateInfo(info)
        showNotification(info.id)
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val act = cm.getNetworkCapabilities(net) ?: return false
        return act.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun showNotification(id: Long) {
        val info = DownloadEngine.activeDownloads.value[id] ?: return
        notificationManager.notify(id.toInt(), createNotification(info))
    }

    private fun createNotification(info: DownloadInfo): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(info.fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(info.state == DownloadState.DOWNLOADING)

        val formatBytes = { bytes: Long -> "%.2f MB".format(bytes / (1024f * 1024f)) }
        
        when (info.state) {
            DownloadState.DOWNLOADING -> {
                builder.setContentText("${formatBytes(info.bytesDownloaded)} / ${if(info.bytesTotal>100) formatBytes(info.bytesTotal) else "Unknown"} - ${formatBytes(info.speed)}/s")
                builder.setProgress(100, (info.progress * 100).toInt(), info.bytesTotal <= 1L)
                
                val pauseIntent = Intent(this, DownloadService::class.java).apply { action="PAUSE"; putExtra("id", info.id) }
                val cancelIntent = Intent(this, DownloadService::class.java).apply { action="CANCEL"; putExtra("id", info.id) }
                
                builder.addAction(0, "Pause", PendingIntent.getService(this, info.id.toInt()+100, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                builder.addAction(0, "Cancel", PendingIntent.getService(this, info.id.toInt()+200, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            DownloadState.PAUSED -> {
                builder.setContentText("Paused. ${info.error ?: ""}")
                val resumeIntent = Intent(this, DownloadService::class.java).apply { action="RESUME"; putExtra("id", info.id) }
                val cancelIntent = Intent(this, DownloadService::class.java).apply { action="CANCEL"; putExtra("id", info.id) }
                builder.addAction(0, "Resume", PendingIntent.getService(this, info.id.toInt()+300, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                builder.addAction(0, "Cancel", PendingIntent.getService(this, info.id.toInt()+200, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            DownloadState.COMPLETED -> {
                builder.setContentText("Download Complete")
                builder.setProgress(0, 0, false)
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            }
            DownloadState.FAILED -> {
                builder.setContentText("Failed: ${info.error}")
                val resumeIntent = Intent(this, DownloadService::class.java).apply { action="RESUME"; putExtra("id", info.id) }
                builder.addAction(0, "Retry", PendingIntent.getService(this, info.id.toInt()+300, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                builder.setSmallIcon(android.R.drawable.stat_notify_error)
            }
            DownloadState.CANCELLED -> {
                builder.setContentText("Cancelled")
            }
            else -> {}
        }
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
