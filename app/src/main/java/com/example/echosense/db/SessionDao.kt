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
}