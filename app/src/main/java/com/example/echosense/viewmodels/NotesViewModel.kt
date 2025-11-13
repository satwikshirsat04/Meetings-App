package com.echosense.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.db.AppDatabase
import com.echosense.models.Session
import com.echosense.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionNoteData(
    val session: Session,
    val noteCount: Int,
    val speakerCount: Int
)

data class NotesUiState(
    val sessions: List<SessionNoteData> = emptyList()
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()
    
    private val database = AppDatabase.getDatabase(application)
    
    fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            database.sessionDao().getAllSessions().collect { sessions ->
                val sessionDataList = sessions.map { session ->
                    val notes = database.noteDao().getNotesForSession(session.id)
                    SessionNoteData(
                        session = session,
                        noteCount = notes.size,
                        speakerCount = session.speakerCount
                    )
                }
                
                _uiState.value = NotesUiState(sessions = sessionDataList)
            }
        }
    }
    
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.sessionDao().deleteSessionById(sessionId)
            database.sessionDao().deleteSpeakersForSession(sessionId)
            database.sessionDao().deleteTranscriptForSession(sessionId)
            database.noteDao().deleteNotesForSession(sessionId)
        }
    }
    
    fun shareSession(context: Context, sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.sessionDao().getSession(sessionId) ?: return@launch
            val transcript = database.sessionDao().getTranscriptForSession(sessionId)
            val notes = database.noteDao().getNotesForSession(sessionId)
            
            ShareUtils.shareAsText(context, session, transcript, notes)
        }
    }
    
    fun shareAllNotes(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val allData = mutableListOf<ShareUtils.SessionData>()
            
            _uiState.value.sessions.forEach { sessionData ->
                val session = sessionData.session
                val speakers = database.sessionDao().getSpeakersForSession(session.id)
                val transcript = database.sessionDao().getTranscriptForSession(session.id)
                val notes = database.noteDao().getNotesForSession(session.id)
                
                allData.add(
                    ShareUtils.SessionData(
                        session = session,
                        speakers = speakers,
                        transcript = transcript,
                        notes = notes
                    )
                )
            }
            
            ShareUtils.shareAllNotes(context, allData)
        }
    }
}