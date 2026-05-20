package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis()
)
