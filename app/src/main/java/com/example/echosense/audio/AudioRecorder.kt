package com.echosense.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class AudioRecorder {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    private val audioDataListeners = mutableListOf<(ShortArray, Int) -> Unit>()
    private val amplitudeListeners = mutableListOf<(Float) -> Unit>()
    
    fun addAudioDataListener(listener: (ShortArray, Int) -> Unit) {
        audioDataListeners.add(listener)
    }
    
    fun addAmplitudeListener(listener: (Float) -> Unit) {
        amplitudeListeners.add(listener)
    }
    
    fun startRecording(outputFile: File? = null): Boolean {
        if (isRecording) return false
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return false
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(bufferSize / 2)
                val fileOutputStream = outputFile?.let { FileOutputStream(it) }
                
                while (isRecording && isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0) {
                        // Write to file
                        fileOutputStream?.let {
                            val byteBuffer = java.nio.ByteBuffer.allocate(readSize * 2)
                            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readSize) {
                                byteBuffer.putShort(buffer[i])
                            }
                            it.write(byteBuffer.array())
                        }
                        
                        // Notify listeners
                        audioDataListeners.forEach { listener ->
                            listener(buffer.copyOf(readSize), readSize)
                        }
                        
                        // Calculate amplitude
                        val amplitude = calculateAmplitude(buffer, readSize)
                        amplitudeListeners.forEach { it(amplitude) }
                    }
                }
                
                fileOutputStream?.close()
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    private fun calculateAmplitude(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        return (sum / size).toFloat()
    }
    
    fun getSampleRate() = sampleRate
}