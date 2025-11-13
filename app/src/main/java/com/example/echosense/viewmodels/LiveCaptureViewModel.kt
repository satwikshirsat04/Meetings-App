package com.echosense.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.audio.AudioRecorder
import com.echosense.audio.SpeakerDiarizer
import com.echosense.db.AppDatabase
import com.echosense.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class LiveCaptureUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isListening: Boolean = false,
    val activeSpeaker: Int? = null,
    val currentAmplitude: Float = 0f,
    val transcriptEntries: List<TranscriptEntry> = emptyList(),
    val currentPartialText: String = "",
    val showEndDialog: Boolean = false,
    val recognitionMode: String = "Initializing...",
    val errorMessage: String = ""
)

class LiveCaptureViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(LiveCaptureUiState())
    val uiState: StateFlow<LiveCaptureUiState> = _uiState.asStateFlow()
    
    private val audioRecorder = AudioRecorder()
    private val speakerDiarizer = SpeakerDiarizer(maxSpeakers = 4)
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isUsingOfflineMode = false
    
    private var currentSessionId: Long = 0
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0
    private var currentDatabase: AppDatabase? = null
    
    fun startRecording(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            currentDatabase = database
            
            // Create session
            val session = Session(
                title = "Conversation ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}",
                startTime = System.currentTimeMillis(),
                endTime = null,
                speakerCount = 0,
                duration = 0L,
                audioFilePath = null,
                isCompleted = false
            )
            
            currentSessionId = database.sessionDao().insertSession(session)
            
            // Create audio file
            audioFile = File(context.filesDir, "audio_$currentSessionId.pcm")
            
            // Setup audio processing
            audioRecorder.addAudioDataListener { audioData, size ->
                processAudioData(audioData, size)
            }
            
            audioRecorder.addAmplitudeListener { amplitude ->
                _uiState.value = _uiState.value.copy(currentAmplitude = amplitude / 32768f)
            }
            
            recordingStartTime = System.currentTimeMillis()
            val started = audioRecorder.startRecording(audioFile)
            
            _uiState.value = _uiState.value.copy(isRecording = started)
            
            // Start speech recognition
            launch(Dispatchers.Main) {
                startSpeechRecognition(context)
            }
        }
    }
    
    private fun startSpeechRecognition(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.value = _uiState.value.copy(
                recognitionMode = "Speech recognition not available",
                errorMessage = "Device does not support speech recognition"
            )
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.value = _uiState.value.copy(
                    isListening = true,
                    recognitionMode = if (isUsingOfflineMode) "Offline Mode" else "Online Mode",
                    errorMessage = ""
                )
            }
            
            override fun onBeginningOfSpeech() {
                _uiState.value = _uiState.value.copy(currentPartialText = "")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Audio level feedback
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    currentPartialText = ""
                )
            }
            
            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    else -> "Unknown error"
                }
                
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    currentPartialText = ""
                )
                
                // If network error and not in offline mode, try offline
                if ((error == SpeechRecognizer.ERROR_NETWORK || 
                     error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) && 
                    !isUsingOfflineMode) {
                    
                    isUsingOfflineMode = true
                    _uiState.value = _uiState.value.copy(
                        recognitionMode = "Switching to Offline Mode..."
                    )
                }
                
                // Auto-restart recognition if recording is active
                if (!_uiState.value.isPaused && _uiState.value.isRecording) {
                    viewModelScope.launch {
                        delay(500)
                        restartSpeechRecognition(context)
                    }
                }
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    if (text.isNotBlank()) {
                        addTranscriptEntry(text)
                    }
                }
                
                // Auto-restart for continuous recognition
                if (!_uiState.value.isPaused && _uiState.value.isRecording) {
                    restartSpeechRecognition(context)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(currentPartialText = matches[0])
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        startListening(context)
    }
    
    private fun startListening(context: Context) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Try to prefer offline recognition
            if (isUsingOfflineMode) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            
            // Continuous listening
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to start recognition: ${e.message}"
            )
        }
    }
    
    private fun restartSpeechRecognition(context: Context) {
        try {
            speechRecognizer?.cancel()
            startListening(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun processAudioData(audioData: ShortArray, size: Int) {
        if (_uiState.value.isPaused) return
        
        viewModelScope.launch(Dispatchers.Default) {
            val currentTime = System.currentTimeMillis() - recordingStartTime
            
            if (size >= audioRecorder.getSampleRate() / 2) {
                val speakerId = speakerDiarizer.processAudioSegment(
                    audioData.copyOf(size),
                    audioRecorder.getSampleRate(),
                    currentTime
                )
                
                if (speakerId != null && speakerId != _uiState.value.activeSpeaker) {
                    _uiState.value = _uiState.value.copy(activeSpeaker = speakerId)
                }
            }
        }
    }
    
    private fun addTranscriptEntry(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis() - recordingStartTime
            val speakerId = _uiState.value.activeSpeaker?.toLong() ?: 0L
            
            val entry = TranscriptEntry(
                sessionId = currentSessionId,
                speakerId = speakerId,
                speakerLabel = "Speaker ${speakerId + 1}",
                text = text,
                timestamp = currentTime,
                duration = text.length * 50L
            )
            
            currentDatabase?.sessionDao()?.insertTranscriptEntry(entry)
            
            val currentEntries = _uiState.value.transcriptEntries.toMutableList()
            currentEntries.add(entry)
            _uiState.value = _uiState.value.copy(
                transcriptEntries = currentEntries,
                currentPartialText = ""
            )
        }
    }
    
    fun togglePause() {
        val isPaused = !_uiState.value.isPaused
        _uiState.value = _uiState.value.copy(isPaused = isPaused)
        
        if (isPaused) {
            speechRecognizer?.cancel()
            _uiState.value = _uiState.value.copy(isListening = false)
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                // Get context from somewhere - you might need to pass it
                // For now, we'll just mark as ready to resume
                _uiState.value = _uiState.value.copy(
                    recognitionMode = "Ready to resume"
                )
            }
        }
    }
    
    fun addBookmark() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis() - recordingStartTime
            // Store bookmark timestamp - can be implemented later
        }
    }
    
    fun showEndDialog() {
        _uiState.value = _uiState.value.copy(showEndDialog = true)
    }
    
    fun dismissEndDialog() {
        _uiState.value = _uiState.value.copy(showEndDialog = false)
    }
    
    fun endRecording(context: Context, onComplete: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Stop speech recognition
            launch(Dispatchers.Main) {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
            
            audioRecorder.stopRecording()
            
            val database = currentDatabase ?: AppDatabase.getDatabase(context)
            val endTime = System.currentTimeMillis()
            val duration = endTime - recordingStartTime
            
            // Update session
            val session = database.sessionDao().getSession(currentSessionId)
            session?.let {
                val updatedSession = it.copy(
                    endTime = endTime,
                    duration = duration,
                    speakerCount = speakerDiarizer.getSpeakerCount(),
                    audioFilePath = audioFile?.absolutePath,
                    isCompleted = true
                )
                database.sessionDao().updateSession(updatedSession)
            }
            
            // Extract notes from real transcript
            val transcript = database.sessionDao().getTranscriptForSession(currentSessionId)
            if (transcript.isNotEmpty()) {
                val extractor = com.echosense.ml.NoteExtractor()
                val extractedNotes = extractor.extractNotes(transcript, currentSessionId)
                
                extractedNotes.forEach { note ->
                    database.noteDao().insertNote(note)
                }
            }
            
            launch(Dispatchers.Main) {
                onComplete(currentSessionId)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        audioRecorder.stopRecording()
    }
}