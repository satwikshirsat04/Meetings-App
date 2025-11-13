package com.echosense.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcript_entries")
data class TranscriptEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val speakerId: Long,
    val speakerLabel: String,
    val text: String,
    val timestamp: Long,
    val duration: Long,
    val confidence: Float = 0.0f
)