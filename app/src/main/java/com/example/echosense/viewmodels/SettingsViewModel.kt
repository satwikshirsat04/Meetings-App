package com.echosense.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val context = application
    
    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear database
                database.clearAllTables()
                
                // Clear audio files
                context.filesDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("audio_")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}