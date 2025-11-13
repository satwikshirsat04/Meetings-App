package com.echosense.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val type: NoteType,
    val content: String,
    val timestamp: Long,
    val speakerId: Long? = null
)

enum class NoteType {
    KEY_POINT,
    ACTION_ITEM,
    DECISION,
    QUESTION,
    BOOKMARK
}