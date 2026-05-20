package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allRecords: Flow<List<DownloadRecord>> = downloadDao.getAllRecords()

    suspend fun insert(record: DownloadRecord) = downloadDao.insertRecord(record)

    suspend fun deleteById(id: Int) = downloadDao.deleteRecordById(id)

    suspend fun clearAll() = downloadDao.clearHistory()
}
