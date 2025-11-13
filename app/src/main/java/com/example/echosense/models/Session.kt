package com.echosense.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long = 0,
    val speakerCount: Int,
    val audioFilePath: String?,
    val isCompleted: Boolean = false
)