package com.echosense.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.audio.WeSpeakerEmbedding
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.sqrt

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
    val maxSpeakers: Int = 4,
    val detectedSpeakers: Int = 0
)

class LiveCaptureViewModel(private val maxSpeakers: Int = 4) : ViewModel() {

    private val TAG = "LiveCaptureVM"

    private val voskDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "VoskThread").apply { priority = Thread.MAX_PRIORITY }
    }.asCoroutineDispatcher()

    private val diarizationDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DiarizationThread").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(LiveCaptureUiState(maxSpeakers = maxSpeakers))
    val uiState: StateFlow<LiveCaptureUiState> = _uiState.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private var weSpeakerEmbedding: WeSpeakerEmbedding? = null
    private lateinit var speakerDiarizer: SpeakerDiarizer
    private var currentContext: Context? = null

    private var currentSessionId: Long = 0
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0
    private var currentDatabase: AppDatabase? = null

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var isModelLoaded = false

    private val voskAccumulator = ByteArrayOutputStream()
    private val MIN_BYTES_FOR_VOSK = 12800

    private val createdSpeakers = mutableSetOf<Int>()

    // FIXED: Separate accumulator for each potential speaker
    private val audioAccumulator = mutableListOf<Short>()
    private val MIN_SAMPLES_FOR_EMBEDDING = 16000  // 1 second minimum
    private val MAX_SAMPLES_FOR_EMBEDDING = 64000  // 4 seconds max (increased!)

    // FIXED: More lenient VAD
    private val SILENCE_THRESHOLD = 500  // Lower for better detection
    private var consecutiveSilentFrames = 0
    private val MAX_SILENT_FRAMES_BEFORE_CLEAR = 30  // 600ms silence (increased!)

    private var lastEmbeddingTime = 0L
    private val EMBEDDING_INTERVAL_MS = 1500L  // Process every 1.5 seconds

    private var frameCount = 0
    private val GC_TRIGGER_INTERVAL = 300

    init {
        speakerDiarizer = SpeakerDiarizer(maxSpeakers = maxSpeakers)
    }

    private fun hasSpeech(audioFrame: ShortArray): Boolean {
        val rms = sqrt(audioFrame.map { it * it.toDouble() }.average())
        return rms > SILENCE_THRESHOLD
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

            try {
                weSpeakerEmbedding = WeSpeakerEmbedding(context)
                Log.d(TAG, "✅ WeSpeaker initialized")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize WeSpeaker", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Speaker detection unavailable",
                        recognitionMode = "STT Only"
                    )
                }
            }

            audioRecorder.addAudioDataListener { frame ->
                processAudioData(frame)
            }

            audioRecorder.addAmplitudeListener { amplitude ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(currentAmplitude = amplitude)
                }
            }

            recordingStartTime = System.currentTimeMillis()
            val started = audioRecorder.startRecording(audioFile)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isRecording = started,
                    processingStatus = if (started) "Recording..." else "Failed to start"
                )
            }

            if (!started) return@launch

            delay(500)
            loadVoskModelSafely(context)
        }
    }

    private fun handleSpeakerChange(newSpeakerId: Int?) {
        viewModelScope.launch(Dispatchers.Main) {
            val previousSpeaker = _uiState.value.activeSpeaker

            if (newSpeakerId != null && newSpeakerId != previousSpeaker) {
                _uiState.value = _uiState.value.copy(activeSpeaker = newSpeakerId)
                ensureSpeakerExists(newSpeakerId)

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Speaker ${newSpeakerId + 1} speaking",
                    detectedSpeakers = speakerDiarizer.getSpeakerCount()
                )

                Log.d(TAG, "🎤 Active Speaker: ${newSpeakerId + 1}, Total: ${speakerDiarizer.getSpeakerCount()}")
            }
        }
    }

    private fun ensureSpeakerExists(speakerId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (createdSpeakers.contains(speakerId)) {
                    return@launch
                }

                val existingSpeaker = currentDatabase?.sessionDao()?.getSpeaker(currentSessionId, speakerId.toLong())

                if (existingSpeaker == null) {
                    val colors = listOf(
                        0xFF00BCD4.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt(),
                        0xFF4CAF50.toInt(), 0xFFE91E63.toInt(), 0xFFFFEB3B.toInt()
                    )
                    val color = colors[speakerId % colors.size]

                    val speaker = Speaker(
                        id = speakerId.toLong(),
                        sessionId = currentSessionId,
                        speakerLabel = "Speaker ${speakerId + 1}",
                        color = color,
                        speakingPercentage = 0f,
                        totalSpeakingTime = 0L
                    )

                    currentDatabase?.sessionDao()?.insertSpeaker(speaker)
                    createdSpeakers.add(speakerId)

                    val speakerCount = currentDatabase?.sessionDao()?.getSpeakerCount(currentSessionId) ?: 0

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(detectedSpeakers = speakerCount)
                    }

                    Log.d(TAG, "✅ Created Speaker ${speakerId + 1}. Database Total: $speakerCount")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating speaker", e)
            }
        }
    }

    private suspend fun loadVoskModelSafely(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading Vosk model...")
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(processingStatus = "Loading speech model...")
            }

            val modelDir = File(context.filesDir, "vosk-model-small-en-us-0.15")

            if (!modelDir.exists() || !File(modelDir, "am/final.mdl").exists()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(processingStatus = "Extracting model...")
                }
                extractAssetToStorage(context, "vosk-model-small-en-us-0.15", modelDir)
            }

            if (!verifyModelFiles(modelDir)) {
                throw IOException("Model files incomplete")
            }

            voskModel = Model(modelDir.absolutePath)
            voskRecognizer = Recognizer(voskModel, audioRecorder.getSampleRate().toFloat())

            voskRecognizer?.setMaxAlternatives(0)
            voskRecognizer?.setWords(true)

            isModelLoaded = true
            Log.d(TAG, "✅ Vosk loaded")

            withContext(Dispatchers.Main) {
                val mode = if (weSpeakerEmbedding != null) "Full AI Mode" else "STT Only"
                _uiState.value = _uiState.value.copy(
                    recognitionMode = mode,
                    processingStatus = "Ready - Speak clearly",
                    isListening = true,
                    errorMessage = ""
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Vosk", e)
            handleModelLoadFailure("STT unavailable: ${e.message}")
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
                extractAssetToStorage(context, fullAssetPath, outFile)
            } else {
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
        return requiredFiles.all { File(modelDir, it).exists() }
    }

    private suspend fun handleModelLoadFailure(message: String) = withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
            recognitionMode = "Speaker Detection Only",
            processingStatus = "STT unavailable",
            errorMessage = message,
            isListening = true
        )
    }

    private fun processAudioData(frame: ShortArray) {
        if (_uiState.value.isPaused) return

        frameCount++

        if (frameCount % GC_TRIGGER_INTERVAL == 0) {
            System.gc()
        }

        val timeFromStart = System.currentTimeMillis() - recordingStartTime
        val hasVoice = hasSpeech(frame)

        if (hasVoice) {
            consecutiveSilentFrames = 0

            // === SPEAKER DIARIZATION - FIXED ===
            if (weSpeakerEmbedding != null) {
                viewModelScope.launch(diarizationDispatcher) {
                    try {
                        // ALWAYS accumulate audio
                        synchronized(audioAccumulator) {
                            audioAccumulator.addAll(frame.toList())

                            // Only trim if too large
                            if (audioAccumulator.size > MAX_SAMPLES_FOR_EMBEDDING) {
                                val excess = audioAccumulator.size - MAX_SAMPLES_FOR_EMBEDDING
                                audioAccumulator.subList(0, excess).clear()
                            }
                        }

                        // Extract embedding at intervals
                        if (timeFromStart - lastEmbeddingTime >= EMBEDDING_INTERVAL_MS) {
                            val currentSize = synchronized(audioAccumulator) { audioAccumulator.size }

                            if (currentSize >= MIN_SAMPLES_FOR_EMBEDDING) {
                                val audioChunk: ShortArray
                                synchronized(audioAccumulator) {
                                    audioChunk = audioAccumulator.toShortArray()
                                    // Keep 0.5s overlap for continuity
                                    val keepSamples = 8000
                                    if (audioAccumulator.size > keepSamples) {
                                        val removeCount = audioAccumulator.size - keepSamples
                                        audioAccumulator.subList(0, removeCount).clear()
                                    }
                                }

                                Log.d(TAG, "🎵 Processing ${audioChunk.size} samples (${audioChunk.size / 16}ms)")

                                val embedding = weSpeakerEmbedding?.extractEmbedding(audioChunk)

                                if (embedding != null && embedding.isNotEmpty()) {
                                    lastEmbeddingTime = timeFromStart

                                    val speakerId = speakerDiarizer.process(embedding, timeFromStart)

                                    Log.d(TAG, "🎯 Diarizer returned: Speaker ${speakerId?.plus(1) ?: "null"}, Total profiles: ${speakerDiarizer.getSpeakerCount()}")

                                    if (speakerId != null) {
                                        handleSpeakerChange(speakerId)
                                    }
                                } else {
                                    Log.w(TAG, "⚠️ Embedding extraction returned null/empty")
                                }
                            } else {
                                Log.d(TAG, "⏳ Waiting for more audio: $currentSize / $MIN_SAMPLES_FOR_EMBEDDING samples")
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Diarization error", e)
                        e.printStackTrace()
                    }
                }
            }
        } else {
            consecutiveSilentFrames++

            // FIXED: Don't clear accumulator too aggressively
            if (consecutiveSilentFrames > MAX_SILENT_FRAMES_BEFORE_CLEAR) {
                synchronized(audioAccumulator) {
                    // Keep last 0.5s instead of clearing completely
                    val keepSamples = 8000
                    if (audioAccumulator.size > keepSamples) {
                        val removeCount = audioAccumulator.size - keepSamples
                        audioAccumulator.subList(0, removeCount).clear()
                        Log.d(TAG, "🧹 Trimmed to ${audioAccumulator.size} samples during silence")
                    }
                }
            }
        }

        // === VOSK STT ===
        viewModelScope.launch(voskDispatcher) {
            try {
                if (!isModelLoaded || voskRecognizer == null) return@launch

                val bytes = frame.toByteArrayLE()

                synchronized(voskAccumulator) {
                    voskAccumulator.write(bytes)
                }

                if (voskAccumulator.size() < MIN_BYTES_FOR_VOSK) return@launch

                val chunk: ByteArray
                synchronized(voskAccumulator) {
                    chunk = voskAccumulator.toByteArray()
                    voskAccumulator.reset()
                }

                val isFinal = voskRecognizer!!.acceptWaveForm(chunk, chunk.size)

                if (isFinal) {
                    val jsonStr = voskRecognizer!!.result
                    handleVoskJson(jsonStr, true)
                } else {
                    val jsonStr = voskRecognizer!!.partialResult
                    handleVoskJson(jsonStr, false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Vosk error", e)
            }
        }
    }

    private suspend fun handleVoskJson(jsonStr: String?, isFinal: Boolean) {
        if (jsonStr == null) return

        try {
            val obj = JSONObject(jsonStr)

            if (!isFinal && obj.has("partial")) {
                val partial = obj.getString("partial")
                if (partial.length > 3) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            currentPartialText = partial,
                            processingStatus = "Listening..."
                        )
                    }
                }
            }

            if (isFinal && obj.has("text")) {
                val text = obj.getString("text").trim()
                if (text.length > 2) {
                    Log.d(TAG, "✅ Recognized: '$text'")
                    addTranscriptEntry(text, 0.85f)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            currentPartialText = "",
                            processingStatus = "Added: ${text.take(20)}..."
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

                ensureSpeakerExists(speakerId.toInt())

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

                withContext(Dispatchers.Main) {
                    val currentEntries = _uiState.value.transcriptEntries.toMutableList()
                    currentEntries.add(entry)
                    _uiState.value = _uiState.value.copy(
                        transcriptEntries = currentEntries
                    )
                }

                Log.d(TAG, "📝 Transcript for Speaker ${speakerId + 1}: $text")
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
                Log.d(TAG, "🛑 Audio stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder", e)
            }

            try {
                voskRecognizer?.close()
                voskRecognizer = null
            } catch (e: Exception) {}

            try {
                voskModel?.close()
                voskModel = null
                isModelLoaded = false
            } catch (e: Exception) {}

            try {
                weSpeakerEmbedding?.close()
                weSpeakerEmbedding = null
            } catch (e: Exception) {}

            try {
                val db = currentDatabase ?: AppDatabase.getDatabase(context)
                val endTime = System.currentTimeMillis()
                val duration = endTime - recordingStartTime

                val actualSpeakerCount = db.sessionDao().getUniqueSpeakerCount(currentSessionId)
                Log.d(TAG, "🎉 FINAL SPEAKERS: $actualSpeakerCount")

                val s = db.sessionDao().getSession(currentSessionId)
                s?.let {
                    val updated = it.copy(
                        endTime = endTime,
                        duration = duration,
                        speakerCount = actualSpeakerCount,
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
            speakerDiarizer.reset()
            weSpeakerEmbedding?.close()

            try {
                (voskDispatcher.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
            } catch (_: Exception) {}

            try {
                (diarizationDispatcher.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
            } catch (_: Exception) {}

            Log.d(TAG, "ViewModel cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCleared", e)
        }
    }

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

private fun SpeakerDiarizer.reset() {
    TODO("Not yet implemented")
}
