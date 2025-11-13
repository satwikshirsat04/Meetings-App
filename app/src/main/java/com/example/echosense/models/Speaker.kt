package com.echosense.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speakers")
data class Speaker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val speakerLabel: String,
    val customName: String? = null,
    val color: Int,
    val speakingPercentage: Float = 0f,
    val totalSpeakingTime: Long = 0,
    val keyTopics: String = ""
)