package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<DownloadRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DownloadRecord)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Query("DELETE FROM download_history")
    suspend fun clearHistory()
}
