package com.echosense.db

import androidx.room.*
import com.echosense.models.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    
    @Insert
    suspend fun insertNote(note: Note): Long
    
    @Update
    suspend fun updateNote(note: Note)
    
    @Delete
    suspend fun deleteNote(note: Note)
    
    @Query("SELECT * FROM notes WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getNotesForSession(sessionId: Long): List<Note>
    
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>
    
    @Query("DELETE FROM notes WHERE sessionId = :sessionId")
    suspend fun deleteNotesForSession(sessionId: Long)
}