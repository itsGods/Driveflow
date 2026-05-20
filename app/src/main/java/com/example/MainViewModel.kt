package com.example

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadRecord
import com.example.data.DownloadRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    HOME, HISTORY, LIBRARY
}

data class ActiveDownload(
    val id: Long,
    val title: String,
    val progress: Float,
    val status: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val speedBytesPerSec: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DownloadRepository
    val historyRecords: StateFlow<List<DownloadRecord>>

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val activeDownloads = _activeDownloads.asStateFlow()

    private val trackedDownloadIds = mutableSetOf<Long>()
    private val lastBytesMap = mutableMapOf<Long, Pair<Long, Long>>() // id -> (time, bytes)
    private var isPolling = false

    init {
        val dao = AppDatabase.getDatabase(application).downloadDao()
        repository = DownloadRepository(dao)
        historyRecords = repository.allRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun startDownload(url: String, context: Context) {
        val fileId = extractFileId(url)
        if (fileId == null) {
            Toast.makeText(context, "Invalid Google Drive link", Toast.LENGTH_SHORT).show()
            return
        }
        val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        val fileName = "DriveFlow_$fileId"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(fileName)
            .setDescription("Fetching file from Cloud...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            trackedDownloadIds.add(downloadId)
            
            viewModelScope.launch {
                repository.insert(DownloadRecord(url = url, fileName = fileName))
            }
            
            Toast.makeText(context, "Download Stream Connected", Toast.LENGTH_SHORT).show()

            if (!isPolling) {
                startPolling(dm)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelDownload(id: Long, context: Context) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(id)
        trackedDownloadIds.remove(id)
        lastBytesMap.remove(id)
    }

    private fun startPolling(dm: DownloadManager) {
        isPolling = true
        viewModelScope.launch {
            while (trackedDownloadIds.isNotEmpty()) {
                try {
                    val query = DownloadManager.Query().setFilterById(*trackedDownloadIds.toLongArray())
                    val cursor = dm.query(query)
                    val currentList = mutableListOf<ActiveDownload>()
                    val completedIds = mutableListOf<Long>()
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                            val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            
                            if (idIndex < 0 || statusIndex < 0) continue

                            val id = cursor.getLong(idIndex)
                            val title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "Unknown" else "Unknown"
                            val status = cursor.getInt(statusIndex)
                            val bytesDownloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                            val bytesTotal = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
                            
                            val progress = if (bytesTotal > 0L) (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f) else 0f
                            val safeProgress = if (progress.isNaN()) 0f else progress
                            
                            val currentTime = System.currentTimeMillis()
                            val previous = lastBytesMap[id]
                            var speed = 0L
                            if (previous != null) {
                                val timeDiff = currentTime - previous.first
                                if (timeDiff > 0L) {
                                    speed = ((bytesDownloaded - previous.second) * 1000L) / timeDiff
                                }
                            }
                            if (speed < 0L) speed = 0L
                            lastBytesMap[id] = Pair(currentTime, bytesDownloaded)

                            currentList.add(ActiveDownload(id, title, safeProgress, status, bytesDownloaded, bytesTotal, speed))
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                completedIds.add(id)
                            }
                        } while (cursor.moveToNext())
                        cursor.close()
                    } else {
                        cursor?.close()
                        trackedDownloadIds.clear()
                    }
                    
                    _activeDownloads.value = currentList
                    trackedDownloadIds.removeAll(completedIds)
                    completedIds.forEach { lastBytesMap.remove(it) }
                } catch(e: Exception) {
                    trackedDownloadIds.clear()
                    _activeDownloads.value = emptyList()
                }
                
                delay(800) // Poll UI every 800ms to stay relatively fresh
            }
            _activeDownloads.value = emptyList()
            isPolling = false
        }
    }

    private fun extractFileId(url: String): String? {
        val patterns = listOf(
            "id=([^&]+)".toRegex(),
            "d/([^/]+)".toRegex(),
            "file/d/([^/]+)".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun deleteRecord(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}
