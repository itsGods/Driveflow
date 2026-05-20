package com.example

import android.app.Application
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
    HOME, HISTORY, LIBRARY
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DownloadRepository
    val historyRecords: StateFlow<List<DownloadRecord>>

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

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

    fun addDownloadRecord(url: String, fileName: String) {
        viewModelScope.launch {
            repository.insert(DownloadRecord(url = url, fileName = fileName))
        }
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
