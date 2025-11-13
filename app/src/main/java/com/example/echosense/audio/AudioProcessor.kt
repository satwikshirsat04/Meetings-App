package com.echosense.audio

import kotlin.math.*

class AudioProcessor {
    
    companion object {
        private const val FFT_SIZE = 512
    }
    
    // Extract MFCC features (simplified version)
    fun extractMFCC(audioData: ShortArray, sampleRate: Int): FloatArray {
        // Convert to float
        val floatData = FloatArray(audioData.size) { audioData[it] / 32768.0f }
        
        // Apply pre-emphasis filter
        val preEmphasized = applyPreEmphasis(floatData)
        
        // Frame the signal
        val frames = frameSignal(preEmphasized, FFT_SIZE, FFT_SIZE / 2)
        
        // Apply Hamming window and compute FFT for each frame
        val mfccFeatures = mutableListOf<FloatArray>()
        for (frame in frames) {
            val windowed = applyHammingWindow(frame)
            val powerSpectrum = computePowerSpectrum(windowed)
            val mfcc = computeMFCC(powerSpectrum, sampleRate)
            mfccFeatures.add(mfcc)
        }
        
        // Average across frames
        return averageFeatures(mfccFeatures)
    }
    
    private fun applyPreEmphasis(signal: FloatArray, alpha: Float = 0.97f): FloatArray {
        val result = FloatArray(signal.size)
        result[0] = signal[0]
        for (i in 1 until signal.size) {
            result[i] = signal[i] - alpha * signal[i - 1]
        }
        return result
    }
    
    private fun frameSignal(signal: FloatArray, frameSize: Int, hopSize: Int): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0
        
        while (start + frameSize <= signal.size) {
            val frame = signal.copyOfRange(start, start + frameSize)
            frames.add(frame)
            start += hopSize
        }
        
        return frames
    }
    
    private fun applyHammingWindow(frame: FloatArray): FloatArray {
        val result = FloatArray(frame.size)
        for (i in frame.indices) {
            val multiplier = 0.54 - 0.46 * cos(2.0 * PI * i / (frame.size - 1))
            result[i] = frame[i] * multiplier.toFloat()
        }
        return result
    }
    
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        // Simplified FFT computation
        val n = frame.size
        val powerSpectrum = FloatArray(n / 2 + 1)
        
        for (k in 0..n / 2) {
            var real = 0.0
            var imag = 0.0
            
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                real += frame[t] * cos(angle)
                imag += frame[t] * sin(angle)
            }
            
            powerSpectrum[k] = (real * real + imag * imag).toFloat()
        }
        
        return powerSpectrum
    }
    
    private fun computeMFCC(powerSpectrum: FloatArray, sampleRate: Int, numCoeffs: Int = 13): FloatArray {
        // Simplified MFCC computation
        val mfcc = FloatArray(numCoeffs)
        
        for (i in 0 until numCoeffs) {
            var sum = 0.0
            for (j in powerSpectrum.indices) {
                sum += powerSpectrum[j] * cos(PI * i * (j + 0.5) / powerSpectrum.size)
            }
            mfcc[i] = sum.toFloat()
        }
        
        return mfcc
    }
    
    private fun averageFeatures(features: List<FloatArray>): FloatArray {
        if (features.isEmpty()) return FloatArray(0)
        
        val avgFeatures = FloatArray(features[0].size)
        for (feature in features) {
            for (i in feature.indices) {
                avgFeatures[i] += feature[i]
            }
        }
        
        for (i in avgFeatures.indices) {
            avgFeatures[i] /= features.size
        }
        
        return avgFeatures
    }
}