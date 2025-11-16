package com.echosense.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.audio.AudioProcessor
import com.example.echosense.audio.AudioRecorder
import com.echosense.audio.SpeakerDiarizer
import com.echosense.db.AppDatabase
import com.echosense.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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
    val errorMessage: String = "",
    val processingStatus: String = "Ready",
    val maxSpeakers: Int = 4
)

class LiveCaptureViewModel(private val maxSpeakers: Int = 4) : ViewModel() {

    private val TAG = "LiveCaptureVM"

    // Single-thread dispatcher for Vosk calls (must be single threaded)
    private val voskDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(LiveCaptureUiState(maxSpeakers = maxSpeakers))
    val uiState: StateFlow<LiveCaptureUiState> = _uiState.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private val audioProcessor = AudioProcessor()
    private lateinit var speakerDiarizer: SpeakerDiarizer
    private var currentContext: Context? = null

    private var currentSessionId: Long = 0
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0
    private var currentDatabase: AppDatabase? = null

    // Vosk offline STT
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var isModelLoaded = false

    // Accumulator to buffer several frames before feeding to Vosk
    private val voskAccumulator = ByteArrayOutputStream()
    private val MIN_BYTES_FOR_VOSK = 3200 // ~200 ms @ 16kHz => 3200 bytes

    init {
        speakerDiarizer = SpeakerDiarizer(maxSpeakers = maxSpeakers)
    }

    fun startRecording(context: Context) {
        currentContext = context

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            currentDatabase = db

            val session = Session(
                title = "Conversation ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}",
                startTime = System.currentTimeMillis(),
                endTime = null,
                speakerCount = 0,
                duration = 0L,
                audioFilePath = null,
                isCompleted = false
            )
            currentSessionId = db.sessionDao().insertSession(session)

            audioFile = File(context.filesDir, "audio_$currentSessionId.pcm")

            // Register audio listeners - recorder emits ShortArray frames (20ms)
            audioRecorder.addAudioDataListener { frame ->
                // frame is ShortArray (320 samples)
                processAudioData(frame)
            }
            audioRecorder.addAmplitudeListener { amplitude ->
                _uiState.value = _uiState.value.copy(currentAmplitude = amplitude)
            }

            recordingStartTime = System.currentTimeMillis()
            val started = audioRecorder.startRecording(audioFile)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isRecording = started,
                    processingStatus = if (started) "Audio recording started" else "Failed to start"
                )
            }

            if (!started) return@launch

            delay(500)

            // Load Vosk model in background
            loadVoskModelSafely(context)
        }
    }

    private suspend fun loadVoskModelSafely(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Vosk model loading...")

            _uiState.value = _uiState.value.copy(
                processingStatus = "Loading speech model..."
            )

            val modelDir = File(context.filesDir, "vosk-model-small-en-us-0.15")

            if (!modelDir.exists() || !File(modelDir, "am/final.mdl").exists()) {
                Log.d(TAG, "Extracting model from assets...")
                _uiState.value = _uiState.value.copy(
                    processingStatus = "Extracting model (first time)..."
                )

                extractAssetToStorage(context, "vosk-model-small-en-us-0.15", modelDir)
                Log.d(TAG, "Model extracted successfully")
            } else {
                Log.d(TAG, "Model already exists, skipping extraction")
            }

            if (!verifyModelFiles(modelDir)) {
                throw IOException("Model files incomplete or corrupted")
            }

            Log.d(TAG, "Loading Vosk model from: ${modelDir.absolutePath}")
            _uiState.value = _uiState.value.copy(
                processingStatus = "Initializing speech engine..."
            )

            voskModel = Model(modelDir.absolutePath)
            val sampleRate = audioRecorder.getSampleRate().toFloat()
            voskRecognizer = Recognizer(voskModel, sampleRate)

            isModelLoaded = true

            Log.d(TAG, "Vosk model loaded successfully (${sampleRate}Hz)")

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    recognitionMode = "Offline (Vosk)",
                    processingStatus = "Ready - Speak now",
                    isListening = true,
                    errorMessage = ""
                )
            }

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory loading Vosk model", e)
            handleModelLoadFailure("Out of memory - Model too large")
        } catch (e: IOException) {
            Log.e(TAG, "IO error loading Vosk model", e)
            handleModelLoadFailure("Model files not found or corrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Vosk model", e)
            handleModelLoadFailure("Failed to load model: ${e.message}")
        }
    }

    private fun extractAssetToStorage(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        if (!targetDir.exists()) targetDir.mkdirs()

        for (name in files) {
            val fullAssetPath = "$assetPath/$name"
            val outFile = File(targetDir, name)

            val children = assetManager.list(fullAssetPath)
            if (children != null && children.isNotEmpty()) {
                // Directory → create same directory inside target
                extractAssetToStorage(context, fullAssetPath, outFile)
            } else {
                // File → copy directly
                assetManager.open(fullAssetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun verifyModelFiles(modelDir: File): Boolean {
        val requiredFiles = listOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "conf/model.conf",
            "graph/HCLr.fst",
            "graph/Gr.fst"
        )

        for (file in requiredFiles) {
            if (!File(modelDir, file).exists()) {
                Log.e(TAG, "Missing required file: $file")
                return false
            }
        }

        return true
    }

    private suspend fun handleModelLoadFailure(message: String) = withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
            recognitionMode = "Speaker Detection Only",
            processingStatus = "STT unavailable",
            errorMessage = message,
            isListening = true
        )
    }

    /**
     * Process incoming short-frame (320 samples):
     * - run speaker diarization
     * - accumulate bytes and feed Vosk only when we have enough buffered audio
     */
    private fun processAudioData(frame: ShortArray) {
        if (_uiState.value.isPaused) return

        // Diarization handled quickly on default dispatcher
        viewModelScope.launch(Dispatchers.Default) {
            val timeFromStart = System.currentTimeMillis() - recordingStartTime

            try {
                val speakerId = speakerDiarizer.processAudioSegment(
                    frame,
                    audioRecorder.getSampleRate(),
                    timeFromStart
                )
                if (speakerId != null && speakerId != _uiState.value.activeSpeaker) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(activeSpeaker = speakerId)
                        Log.d(TAG, "Active speaker: ${speakerId + 1}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Speaker diarization error", e)
            }

            // Convert ShortArray -> bytes (LE) for Vosk
            val bytes = frame.toByteArrayLE()

            // Feed Vosk through single-thread dispatcher (must be single-threaded)
            if (isModelLoaded && voskRecognizer != null) {
                // accumulate
                synchronized(voskAccumulator) {
                    voskAccumulator.write(bytes)
                }

                // Only process when we have enough bytes (e.g. >= 200ms)
                if (voskAccumulator.size() < MIN_BYTES_FOR_VOSK) return@launch

                // Extract chunk to send
                val chunk: ByteArray
                synchronized(voskAccumulator) {
                    chunk = voskAccumulator.toByteArray()
                    voskAccumulator.reset()
                }

                // Call Vosk on single thread
                viewModelScope.launch(voskDispatcher) {
                    try {
                        val isFinal = voskRecognizer!!.acceptWaveForm(chunk, chunk.size)
                        if (isFinal) {
                            val jsonStr = voskRecognizer!!.result
                            handleVoskJson(jsonStr, true)
                        } else {
                            val jsonStr = voskRecognizer!!.partialResult
                            handleVoskJson(jsonStr, false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Vosk processing error", e)
                    }
                }
            }
        }
    }

    private suspend fun handleVoskJson(jsonStr: String?, isFinal: Boolean) {
        if (jsonStr == null) return

        try {
            val obj = JSONObject(jsonStr)

            if (!isFinal && obj.has("partial")) {
                val partial = obj.getString("partial")
                if (partial.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            currentPartialText = partial,
                            processingStatus = "Recognizing..."
                        )
                    }
                }
            }

            if (isFinal && obj.has("text")) {
                val text = obj.getString("text")
                if (text.isNotBlank()) {
                    Log.d(TAG, "Recognized: '$text'")
                    addTranscriptEntry(text, 0.85f)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            currentPartialText = "",
                            processingStatus = "Added transcript"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
        }
    }

    private fun addTranscriptEntry(text: String, confidence: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis() - recordingStartTime
                val speakerId = _uiState.value.activeSpeaker?.toLong() ?: 0L

                val entry = TranscriptEntry(
                    sessionId = currentSessionId,
                    speakerId = speakerId,
                    speakerLabel = "Speaker ${speakerId + 1}",
                    text = text,
                    timestamp = currentTime,
                    duration = text.split(" ").size * 400L,
                    confidence = confidence
                )

                currentDatabase?.sessionDao()?.insertTranscriptEntry(entry)
                val currentEntries = _uiState.value.transcriptEntries.toMutableList()
                currentEntries.add(entry)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        transcriptEntries = currentEntries
                    )
                }

                Log.d(TAG, "Transcript added: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding transcript", e)
            }
        }
    }

    fun togglePause() {
        val isPaused = !_uiState.value.isPaused
        _uiState.value = _uiState.value.copy(
            isPaused = isPaused,
            isListening = !isPaused,
            processingStatus = if (isPaused) "Paused" else "Listening..."
        )
    }

    fun addBookmark() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis() - recordingStartTime
                val bookmark = Note(
                    sessionId = currentSessionId,
                    type = NoteType.BOOKMARK,
                    content = "Bookmark at ${formatTime(currentTime)}",
                    timestamp = currentTime
                )
                currentDatabase?.noteDao()?.insertNote(bookmark)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(processingStatus = "Bookmark added")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding bookmark", e)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        return String.format("%02d:%02d", minutes, seconds % 60)
    }

    fun showEndDialog() {
        _uiState.value = _uiState.value.copy(showEndDialog = true)
    }

    fun dismissEndDialog() {
        _uiState.value = _uiState.value.copy(showEndDialog = false)
    }

    fun endRecording(context: Context, onComplete: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                audioRecorder.stopRecording()
                Log.d(TAG, "Audio recorder stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder", e)
            }

            // Close Vosk resources safely
            try {
                voskRecognizer?.close()
                voskRecognizer = null
                Log.d(TAG, "Recognizer closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing recognizer", e)
            }

            try {
                voskModel?.close()
                voskModel = null
                isModelLoaded = false
                Log.d(TAG, "Model closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing model", e)
            }

            try {
                val db = currentDatabase ?: AppDatabase.getDatabase(context)
                val endTime = System.currentTimeMillis()
                val duration = endTime - recordingStartTime

                val s = db.sessionDao().getSession(currentSessionId)
                s?.let {
                    val updated = it.copy(
                        endTime = endTime,
                        duration = duration,
                        speakerCount = speakerDiarizer.getSpeakerCount(),
                        audioFilePath = audioFile?.absolutePath,
                        isCompleted = true
                    )
                    db.sessionDao().updateSession(updated)
                }

                val transcript = db.sessionDao().getTranscriptForSession(currentSessionId)

                if (transcript.isNotEmpty()) {
                    val extractor = com.echosense.ml.NoteExtractor()
                    val notes = extractor.extractNotes(transcript, currentSessionId)
                    notes.forEach { note -> db.noteDao().insertNote(note) }
                }

                withContext(Dispatchers.Main) {
                    onComplete(currentSessionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            voskRecognizer?.close()
            voskModel?.close()
            audioRecorder.stopRecording()
            // shut down vosk dispatcher
            try {
                (voskDispatcher.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
            } catch (_: Exception) {}
            Log.d(TAG, "ViewModel cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCleared", e)
        }
    }

    // --- helpers ---

    private fun ShortArray.toByteArrayLE(): ByteArray {
        val b = ByteArray(this.size * 2)
        var idx = 0
        for (s in this) {
            val v = s.toInt()
            b[idx++] = (v and 0xFF).toByte()
            b[idx++] = ((v shr 8) and 0xFF).toByte()
        }
        return b
    }
}
