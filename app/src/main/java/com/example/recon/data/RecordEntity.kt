package com.example.recon.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [Index(value = ["filePath"], unique = true), Index(value = ["startedAt"])],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMillis: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
