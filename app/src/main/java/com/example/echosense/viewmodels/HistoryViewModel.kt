package com.echosense.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.db.AppDatabase
import com.echosense.models.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<Session> = emptyList()
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private val database = AppDatabase.getDatabase(application)
    
    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.sessionDao().getAllSessions().collect { sessions ->
                _uiState.value = HistoryUiState(sessions = sessions)
            }
        }
    }
}