package com.example

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadRecord
import com.example.data.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    HOME, HISTORY, LIBRARY, SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DownloadRepository
    val historyRecords: StateFlow<List<DownloadRecord>>

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly = _wifiOnly.asStateFlow()

    private val _concurrentLimit = MutableStateFlow(4)
    val concurrentLimit = _concurrentLimit.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        _wifiOnly.value = value
        DownloadEngine.wifiOnly = value
    }

    fun setConcurrentLimit(value: Int) {
        _concurrentLimit.value = value
        DownloadEngine.concurrentLimit = value
    }

    val activeDownloads = DownloadEngine.activeDownloads

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
        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var downloadUrl = finalUrl
            val fileName: String

            if (finalUrl.contains("drive.google.com")) {
                val fileId = extractFileId(finalUrl)
                if (fileId != null) {
                    fileName = "Drive_${fileId}.ext"
                    downloadUrl = try {
                        resolveDriveUrl(fileId)
                    } catch (e: Exception) {
                        "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Invalid Google Drive link", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            } else {
                val guessedName = android.webkit.URLUtil.guessFileName(finalUrl, null, null)
                fileName = if (guessedName.isNullOrBlank() || guessedName == "bin") "Download_${System.currentTimeMillis()}" else guessedName
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                enqueueDownload(downloadUrl, fileName, url, context)
            }
        }
    }

    private fun resolveDriveUrl(fileId: String): String {
        val initialUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        val connection = java.net.URL(initialUrl).openConnection() as java.net.HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connect()

        val cookie = connection.getHeaderField("Set-Cookie")?.split(";")?.get(0)
        
        if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
            val scanner = java.util.Scanner(connection.inputStream).useDelimiter("\\A")
            val html = if (scanner.hasNext()) scanner.next() else ""
            val confirmRegex = "confirm=([^&\"'>]+)".toRegex()
            val match = confirmRegex.find(html)
            if (match != null) {
                val confirmToken = match.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$fileId&confirm=$confirmToken"
            }
        }
        return "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
    }

    private fun enqueueDownload(downloadUrl: String, fileName: String, originalUrl: String, context: Context) {
        DownloadEngine.enqueue(context, downloadUrl, fileName)
        Toast.makeText(context, "Download processing started", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            repository.insert(DownloadRecord(url = originalUrl, fileName = fileName))
        }
    }

    fun cancelDownload(id: Long, context: Context) {
        DownloadEngine.cancel(id)
    }
    
    fun togglePause(id: Long, context: Context) {
        val info = activeDownloads.value[id] ?: return
        if (info.state == DownloadState.PAUSED || info.state == DownloadState.FAILED) {
            DownloadEngine.resume(context, id)
        } else {
            DownloadEngine.pause(id)
        }
    }

    fun clearFinishedDownloads() {
        DownloadEngine.clearFinished()
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
