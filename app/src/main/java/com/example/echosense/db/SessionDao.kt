package com.echosense.db

import androidx.room.*
import com.echosense.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Insert
    suspend fun insertSession(session: Session): Long
    
    @Update
    suspend fun updateSession(session: Session)
    
    @Delete
    suspend fun deleteSession(session: Session)
    
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): Session?
    
    @Insert
    suspend fun insertSpeaker(speaker: Speaker): Long
    
    @Query("SELECT * FROM speakers WHERE sessionId = :sessionId")
    suspend fun getSpeakersForSession(sessionId: Long): List<Speaker>
    
    // ADD THESE CRITICAL METHODS:
    @Query("SELECT * FROM speakers WHERE sessionId = :sessionId AND id = :speakerId")
    suspend fun getSpeaker(sessionId: Long, speakerId: Long): Speaker?
    
    @Query("SELECT COUNT(DISTINCT speakerId) FROM transcript_entries WHERE sessionId = :sessionId")
    suspend fun getUniqueSpeakerCount(sessionId: Long): Int
    
    @Query("SELECT COUNT(*) FROM speakers WHERE sessionId = :sessionId")
    suspend fun getSpeakerCount(sessionId: Long): Int
    
    @Query("DELETE FROM speakers WHERE sessionId = :sessionId AND id = :speakerId")
    suspend fun deleteSpeaker(sessionId: Long, speakerId: Long)
    
    @Insert
    suspend fun insertTranscriptEntry(entry: TranscriptEntry): Long
    
    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTranscriptForSession(sessionId: Long): List<TranscriptEntry>
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
    
    @Query("DELETE FROM speakers WHERE sessionId = :sessionId")
    suspend fun deleteSpeakersForSession(sessionId: Long)
    
    @Query("DELETE FROM transcript_entries WHERE sessionId = :sessionId")
    suspend fun deleteTranscriptForSession(sessionId: Long)
    
    // Clear all tables (for settings)
    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()
    
    @Query("DELETE FROM speakers")
    suspend fun clearAllSpeakers()
    
    @Query("DELETE FROM transcript_entries")
    suspend fun clearAllTranscripts()
    
    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()
}