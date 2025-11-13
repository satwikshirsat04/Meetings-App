package com.echosense.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echosense.db.AppDatabase
import com.echosense.models.*
import com.echosense.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionSummaryUiState(
    val session: Session? = null,
    val speakers: List<Speaker> = emptyList(),
    val transcript: List<TranscriptEntry> = emptyList(),
    val notes: List<Note> = emptyList()
)

class SessionSummaryViewModel(
    private val context: Context,
    private val sessionId: Long
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionSummaryUiState())
    val uiState: StateFlow<SessionSummaryUiState> = _uiState.asStateFlow()
    
    private val database = AppDatabase.getDatabase(context)
    
    fun loadSessionData() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.sessionDao().getSession(sessionId)
            val speakers = database.sessionDao().getSpeakersForSession(sessionId)
            val transcript = database.sessionDao().getTranscriptForSession(sessionId)
            val notes = database.noteDao().getNotesForSession(sessionId)
            
            _uiState.value = SessionSummaryUiState(
                session = session,
                speakers = speakers,
                transcript = transcript,
                notes = notes
            )
        }
    }
    
    fun deleteSession(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            database.sessionDao().deleteSessionById(sessionId)
            database.sessionDao().deleteSpeakersForSession(sessionId)
            database.sessionDao().deleteTranscriptForSession(sessionId)
            database.noteDao().deleteNotesForSession(sessionId)
            
            launch(Dispatchers.Main) {
                onComplete()
            }
        }
    }
    
    fun shareAsText(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.session?.let { session ->
                ShareUtils.shareAsText(
                    context,
                    session,
                    _uiState.value.transcript,
                    _uiState.value.notes
                )
            }
        }
    }
    
    fun shareAsPDF(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.session?.let { session ->
                ShareUtils.shareAsPDF(
                    context,
                    session,
                    _uiState.value.transcript,
                    _uiState.value.notes
                )
            }
        }
    }
    
    fun shareAsJSON(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.session?.let { session ->
                ShareUtils.shareAsJSON(
                    context,
                    session,
                    _uiState.value.speakers,
                    _uiState.value.transcript,
                    _uiState.value.notes
                )
            }
        }
    }
}

class SessionSummaryViewModelFactory(
    private val context: Context,
    private val sessionId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionSummaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SessionSummaryViewModel(context, sessionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}