package com.example.echosense.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * AudioRecorder that emits fixed-size ShortArray frames (320 samples = 20ms @ 16kHz).
 * Safe for feeding into Vosk when framed/accumulated by ViewModel.
 */
class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // 20 ms frames -> 320 samples @ 16kHz
    private val frameSize = 320
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSizeInBytes = if (minBufferSize <= 0) frameSize * 2 * 4 else minBufferSize

    private val audioDataListeners = mutableListOf<(ShortArray) -> Unit>()
    private val amplitudeListeners = mutableListOf<(Float) -> Unit>()

    fun addAudioDataListener(listener: (ShortArray) -> Unit) {
        audioDataListeners.add(listener)
    }

    fun removeAudioDataListener(listener: (ShortArray) -> Unit) {
        audioDataListeners.remove(listener)
    }

    fun addAmplitudeListener(listener: (Float) -> Unit) {
        amplitudeListeners.add(listener)
    }

    fun removeAmplitudeListener(listener: (Float) -> Unit) {
        amplitudeListeners.remove(listener)
    }

    /**
     * Start recording. If outputFile is provided, raw PCM16 LE bytes are written to that file.
     * Returns true if recording started successfully.
     */
    fun startRecording(outputFile: File? = null): Boolean {
        if (isRecording) return true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInBytes
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            val frameBuffer = ShortArray(frameSize)
            val fileOut = outputFile?.let { FileOutputStream(it) }

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    while (isRecording && isActive) {
                        val read = audioRecord?.read(frameBuffer, 0, frameSize) ?: 0
                        if (read == frameSize) {
                            // Notify listeners with a copy (so downstream can modify safely)
                            val copied = frameBuffer.copyOf()
                            audioDataListeners.forEach { it(copied) }

                            // amplitude (normalized 0..1)
                            val amp = frameBuffer.map { abs(it.toInt()) }.average().toFloat() / 32768f
                            amplitudeListeners.forEach { it(amp) }

                            // Write to file if requested (PCM16 LE)
                            fileOut?.write(copied.toByteArrayLittleEndian())
                        } else {
                            // small pause to avoid busy spin if read isn't full frame
                            delay(2)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { fileOut?.close() } catch (_: Exception) {}
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        try { recordingJob?.cancel() } catch (_: Exception) {}
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    fun getSampleRate() = sampleRate
}

// Helper: convert ShortArray -> ByteArray Little Endian
private fun ShortArray.toByteArrayLittleEndian(): ByteArray {
    val b = ByteArray(this.size * 2)
    for (i in this.indices) {
        val v = this[i].toInt()
        b[i * 2] = (v and 0xFF).toByte()
        b[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return b
}
