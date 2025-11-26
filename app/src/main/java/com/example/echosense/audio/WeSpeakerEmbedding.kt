//package com.echosense.audio
//
//import android.content.Context
//import android.util.Log
//import ai.onnxruntime.*
//import java.io.File
//import java.nio.FloatBuffer
//import kotlin.math.*
//
//class WeSpeakerEmbedding(context: Context) {
//
//    private val TAG = "WeSpeaker"
//    private var ortSession: OrtSession? = null
//    private var ortEnv: OrtEnvironment? = null
//    private var isClosed = false
//
//    companion object {
//        private const val MODEL_NAME = "ecapa_tdnn_512.onnx"
//        private const val SAMPLE_RATE = 16000
//        private const val MIN_AUDIO_LENGTH = 16000 // 1 second minimum
//
//        // Fbank parameters
//        private const val FRAME_LENGTH = 400  // 25ms at 16kHz
//        private const val FRAME_SHIFT = 160   // 10ms at 16kHz
//        private const val NUM_MEL_BINS = 80
//    }
//
//    init {
//        loadModel(context)
//    }
//
//    private fun loadModel(context: Context) {
//        try {
//            Log.d(TAG, "Loading WeSpeaker ONNX model...")
//
//            val modelFile = File(context.filesDir, MODEL_NAME)
//
//            // Copy model from assets if not exists
//            if (!modelFile.exists()) {
//                context.assets.open(MODEL_NAME).use { input ->
//                    modelFile.outputStream().use { output ->
//                        input.copyTo(output)
//                    }
//                }
//                Log.d(TAG, "Model copied from assets")
//            }
//
//            ortEnv = OrtEnvironment.getEnvironment()
//            val sessionOptions = OrtSession.SessionOptions()
//            sessionOptions.setIntraOpNumThreads(2)
//            sessionOptions.setInterOpNumThreads(2)
//
//            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
//
//            Log.d(TAG, "✅ WeSpeaker model loaded successfully")
//
//            // Log input/output info
//            ortSession?.inputInfo?.forEach { (name, info) ->
//                Log.d(TAG, "Input: $name -> ${info.info}")
//            }
//            ortSession?.outputInfo?.forEach { (name, info) ->
//                Log.d(TAG, "Output: $name -> ${info.info}")
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "❌ Failed to load WeSpeaker model", e)
//        }
//    }
//
//    @Synchronized
//    fun extractEmbedding(audioData: ShortArray): FloatArray? {
//        if (isClosed || ortSession == null) {
//            Log.w(TAG, "Model closed or not loaded")
//            return null
//        }
//
//        if (audioData.size < MIN_AUDIO_LENGTH) {
//            Log.w(TAG, "Audio too short: ${audioData.size} samples")
//            return null
//        }
//
//        try {
//            // Convert to float and normalize
//            val floatAudio = FloatArray(audioData.size) {
//                audioData[it] / 32768f
//            }
//
//            // Extract Fbank features
//            val fbank = extractFbank(floatAudio)
//
//            if (fbank.isEmpty()) {
//                Log.w(TAG, "Failed to extract Fbank features")
//                return null
//            }
//
//            val numFrames = fbank.size / NUM_MEL_BINS
//            Log.d(TAG, "Extracted Fbank: $numFrames frames x $NUM_MEL_BINS bins")
//
//            // Create ONNX tensor - shape: [1, num_frames, num_mel_bins]
//            val shape = longArrayOf(1, numFrames.toLong(), NUM_MEL_BINS.toLong())
//
//            val inputTensor = OnnxTensor.createTensor(
//                ortEnv,
//                FloatBuffer.wrap(fbank),
//                shape
//            )
//
//            // Run inference with correct input name
//            val inputs = mapOf("feats" to inputTensor)
//            val outputs = ortSession?.run(inputs)
//
//            // Get embedding output
//            val outputTensor = outputs?.get(0)?.value
//
//            val embedding = when (outputTensor) {
//                is Array<*> -> {
//                    if (outputTensor.isArrayOf<FloatArray>()) {
//                        @Suppress("UNCHECKED_CAST")
//                        (outputTensor as Array<FloatArray>)[0]
//                    } else null
//                }
//                is FloatArray -> outputTensor
//                else -> null
//            }
//
//            // Cleanup
//            inputTensor.close()
//            outputs?.close()
//
//            if (embedding != null) {
//                // Normalize embedding
//                val normalized = l2Normalize(embedding)
//                Log.d(TAG, "✅ Extracted ${normalized.size}-dim embedding")
//                return normalized
//            } else {
//                Log.e(TAG, "Failed to extract embedding from output")
//                return null
//            }
//
//        } catch (e: Exception) {
//            if (!isClosed) {
//                Log.e(TAG, "Error extracting embedding", e)
//            }
//            return null
//        }
//    }
//
//    private fun extractFbank(audio: FloatArray): FloatArray {
//        try {
//            // Pre-emphasis
//            val emphasized = preEmphasis(audio)
//
//            // Frame the signal
//            val frames = frameSignal(emphasized, FRAME_LENGTH, FRAME_SHIFT)
//
//            if (frames.isEmpty()) {
//                Log.w(TAG, "No frames extracted")
//                return FloatArray(0)
//            }
//
//            val fbankFeatures = mutableListOf<FloatArray>()
//
//            for (frame in frames) {
//                // Apply Hamming window
//                val windowed = applyHamming(frame)
//
//                // Compute power spectrum
//                val powerSpectrum = computePowerSpectrum(windowed)
//
//                // Apply Mel filterbank
//                val melEnergies = applyMelFilterbank(powerSpectrum)
//
//                // Apply log
//                val logMel = FloatArray(melEnergies.size) {
//                    ln(maxOf(melEnergies[it], 1e-10f))
//                }
//
//                fbankFeatures.add(logMel)
//            }
//
//            // Flatten to 1D array
//            val totalSize = fbankFeatures.size * NUM_MEL_BINS
//            val result = FloatArray(totalSize)
//            var idx = 0
//            for (frame in fbankFeatures) {
//                for (value in frame) {
//                    result[idx++] = value
//                }
//            }
//
//            Log.d(TAG, "Fbank extraction: ${fbankFeatures.size} frames")
//            return result
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error in Fbank extraction", e)
//            return FloatArray(0)
//        }
//    }
//
//    private fun preEmphasis(signal: FloatArray, alpha: Float = 0.97f): FloatArray {
//        if (signal.isEmpty()) return signal
//        val result = FloatArray(signal.size)
//        result[0] = signal[0]
//        for (i in 1 until signal.size) {
//            result[i] = signal[i] - alpha * signal[i - 1]
//        }
//        return result
//    }
//
//    private fun frameSignal(signal: FloatArray, frameLength: Int, frameShift: Int): List<FloatArray> {
//        val frames = mutableListOf<FloatArray>()
//        var start = 0
//        while (start + frameLength <= signal.size) {
//            frames.add(signal.copyOfRange(start, start + frameLength))
//            start += frameShift
//        }
//        return frames
//    }
//
//    private fun applyHamming(frame: FloatArray): FloatArray {
//        val result = FloatArray(frame.size)
//        for (i in frame.indices) {
//            val window = 0.54 - 0.46 * cos(2.0 * PI * i / (frame.size - 1))
//            result[i] = frame[i] * window.toFloat()
//        }
//        return result
//    }
//
//    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
//        val fftSize = nextPowerOfTwo(frame.size)
//        val complex = FloatArray(fftSize * 2)
//
//        // Copy frame data
//        for (i in frame.indices) {
//            complex[2 * i] = frame[i]
//            complex[2 * i + 1] = 0f
//        }
//
//        // Perform FFT
//        fftRadix2(complex, fftSize)
//
//        // Compute power spectrum
//        val power = FloatArray(fftSize / 2 + 1)
//        for (i in power.indices) {
//            val real = complex[2 * i]
//            val imag = complex[2 * i + 1]
//            power[i] = real * real + imag * imag
//        }
//        return power
//    }
//
//    private fun nextPowerOfTwo(n: Int): Int {
//        var power = 1
//        while (power < n) power *= 2
//        return power
//    }
//
//    private fun fftRadix2(x: FloatArray, n: Int) {
//        // Bit reversal
//        var j = 0
//        for (i in 0 until n - 1) {
//            if (i < j) {
//                var temp = x[2 * i]
//                x[2 * i] = x[2 * j]
//                x[2 * j] = temp
//
//                temp = x[2 * i + 1]
//                x[2 * i + 1] = x[2 * j + 1]
//                x[2 * j + 1] = temp
//            }
//
//            var k = n / 2
//            while (k <= j) {
//                j -= k
//                k /= 2
//            }
//            j += k
//        }
//
//        // FFT computation
//        var len = 2
//        while (len <= n) {
//            val angle = -2.0 * PI / len
//            val wReal = cos(angle).toFloat()
//            val wImag = sin(angle).toFloat()
//
//            var i = 0
//            while (i < n) {
//                var wRealCurrent = 1f
//                var wImagCurrent = 0f
//
//                for (j in 0 until len / 2) {
//                    val idx1 = 2 * (i + j)
//                    val idx2 = 2 * (i + j + len / 2)
//
//                    val real1 = x[idx1]
//                    val imag1 = x[idx1 + 1]
//                    val real2 = x[idx2]
//                    val imag2 = x[idx2 + 1]
//
//                    val tReal = wRealCurrent * real2 - wImagCurrent * imag2
//                    val tImag = wRealCurrent * imag2 + wImagCurrent * real2
//
//                    x[idx1] = real1 + tReal
//                    x[idx1 + 1] = imag1 + tImag
//                    x[idx2] = real1 - tReal
//                    x[idx2 + 1] = imag1 - tImag
//
//                    val temp = wRealCurrent
//                    wRealCurrent = temp * wReal - wImagCurrent * wImag
//                    wImagCurrent = temp * wImag + wImagCurrent * wReal
//                }
//                i += len
//            }
//            len *= 2
//        }
//    }
//
//    private fun applyMelFilterbank(powerSpectrum: FloatArray): FloatArray {
//        val lowMel = hzToMel(0f)
//        val highMel = hzToMel(SAMPLE_RATE / 2f)
//
//        val melPoints = FloatArray(NUM_MEL_BINS + 2) {
//            lowMel + (it.toFloat() / (NUM_MEL_BINS + 1)) * (highMel - lowMel)
//        }
//        val hzPoints = melPoints.map { melToHz(it) }.toFloatArray()
//
//        val fftSize = (powerSpectrum.size - 1) * 2
//        val bins = hzPoints.map {
//            ((fftSize + 1) * it / SAMPLE_RATE).toInt().coerceIn(0, powerSpectrum.size - 1)
//        }.toIntArray()
//
//        val fbank = FloatArray(NUM_MEL_BINS)
//
//        for (m in 1..NUM_MEL_BINS) {
//            val f_m_minus = bins[m - 1]
//            val f_m = bins[m]
//            val f_m_plus = bins[m + 1]
//
//            var sum = 0f
//
//            for (k in f_m_minus until f_m) {
//                val weight = (k - f_m_minus).toFloat() / maxOf(1, f_m - f_m_minus)
//                sum += weight * powerSpectrum.getOrElse(k) { 0f }
//            }
//
//            for (k in f_m until f_m_plus) {
//                val weight = (f_m_plus - k).toFloat() / maxOf(1, f_m_plus - f_m)
//                sum += weight * powerSpectrum.getOrElse(k) { 0f }
//            }
//
//            fbank[m - 1] = sum
//        }
//
//        return fbank
//    }
//
//    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
//    private fun melToHz(mel: Float) = 700f * (10f.pow(mel / 2595f) - 1f)
//
//    private fun l2Normalize(vec: FloatArray): FloatArray {
//        var norm = 0f
//        for (v in vec) {
//            norm += v * v
//        }
//        norm = sqrt(norm)
//
//        if (norm < 1e-12f) return vec
//
//        return FloatArray(vec.size) { vec[it] / norm }
//    }
//
//    @Synchronized
//    fun close() {
//        if (isClosed) return
//
//        isClosed = true
//        try {
//            ortSession?.close()
//            ortSession = null
//            ortEnv?.close()
//            ortEnv = null
//            Log.d(TAG, "WeSpeaker resources released")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error closing resources", e)
//        }
//    }
//}

package com.echosense.audio

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.*

class WeSpeakerEmbedding(context: Context) {

    private val TAG = "WeSpeaker"
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var isClosed = false

    companion object {
        private const val MODEL_NAME = "ecapa_tdnn_512.onnx"
        private const val SAMPLE_RATE = 16000
        private const val MIN_AUDIO_LENGTH = 16000 // 1 second minimum

        // Fbank parameters
        private const val FRAME_LENGTH = 400  // 25ms at 16kHz
        private const val FRAME_SHIFT = 160   // 10ms at 16kHz
        private const val NUM_MEL_BINS = 80
    }

    init {
        loadModel(context)
    }

    private fun loadModel(context: Context) {
        try {
            Log.d(TAG, "Loading WeSpeaker ONNX model...")

            val modelFile = File(context.filesDir, MODEL_NAME)

            // Copy model from assets if not exists
            if (!modelFile.exists()) {
                Log.d(TAG, "Model not found, copying from assets...")
                context.assets.open(MODEL_NAME).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Model copied from assets to ${modelFile.absolutePath}")
            } else {
                Log.d(TAG, "Model already exists at ${modelFile.absolutePath}")
            }

            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(2)
            sessionOptions.setInterOpNumThreads(2)
//            sessionOptions.optimizationLevel = OrtSession.SessionOptions.OptLevel.ALL_OPT
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)

            Log.d(TAG, "✅ WeSpeaker model loaded successfully")

            // Log input/output info
            ortSession?.inputInfo?.forEach { (name, info) ->
                Log.d(TAG, "Input: $name -> ${info.info}")
            }
            ortSession?.outputInfo?.forEach { (name, info) ->
                Log.d(TAG, "Output: $name -> ${info.info}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load WeSpeaker model", e)
            throw RuntimeException("Failed to load WeSpeaker model", e)
        }
    }

    @Synchronized
    fun extractEmbedding(audioData: ShortArray): FloatArray? {
        if (isClosed || ortSession == null) {
            Log.w(TAG, "Model closed or not loaded")
            return null
        }

        if (audioData.size < MIN_AUDIO_LENGTH) {
            Log.w(TAG, "Audio too short: ${audioData.size} samples (min: $MIN_AUDIO_LENGTH)")
            return null
        }

        try {
            // Convert to float and normalize
            val floatAudio = FloatArray(audioData.size) {
                audioData[it] / 32768f
            }

            // Extract Fbank features
            val fbank = extractFbank(floatAudio)

            if (fbank.isEmpty()) {
                Log.w(TAG, "Failed to extract Fbank features")
                return null
            }

            val numFrames = fbank.size / NUM_MEL_BINS
            Log.d(TAG, "Extracted Fbank: $numFrames frames x $NUM_MEL_BINS bins")

            // Create ONNX tensor - shape: [1, num_frames, num_mel_bins]
            val shape = longArrayOf(1, numFrames.toLong(), NUM_MEL_BINS.toLong())

            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(fbank),
                shape
            )

            // Run inference with correct input name
            val inputs = mapOf("feats" to inputTensor)
            val outputs = ortSession?.run(inputs)

            // Get embedding output
            val outputTensor = outputs?.get(0)?.value

            val embedding = when (outputTensor) {
                is Array<*> -> {
                    if (outputTensor.isArrayOf<FloatArray>()) {
                        @Suppress("UNCHECKED_CAST")
                        (outputTensor as Array<FloatArray>)[0]
                    } else null
                }
                is FloatArray -> outputTensor
                else -> null
            }

            // Cleanup
            inputTensor.close()
            outputs?.close()

            if (embedding != null) {
                // Normalize embedding
                val normalized = l2Normalize(embedding)
                Log.d(TAG, "✅ Extracted ${normalized.size}-dim embedding")
                return normalized
            } else {
                Log.e(TAG, "Failed to extract embedding from output")
                return null
            }

        } catch (e: Exception) {
            if (!isClosed) {
                Log.e(TAG, "Error extracting embedding", e)
            }
            return null
        }
    }

    // Rest of the WeSpeakerEmbedding class remains the same...
    // [Include all the existing Fbank extraction methods unchanged]

    private fun extractFbank(audio: FloatArray): FloatArray {
        try {
            // Pre-emphasis
            val emphasized = preEmphasis(audio)

            // Frame the signal
            val frames = frameSignal(emphasized, FRAME_LENGTH, FRAME_SHIFT)

            if (frames.isEmpty()) {
                Log.w(TAG, "No frames extracted")
                return FloatArray(0)
            }

            val fbankFeatures = mutableListOf<FloatArray>()

            for (frame in frames) {
                // Apply Hamming window
                val windowed = applyHamming(frame)

                // Compute power spectrum
                val powerSpectrum = computePowerSpectrum(windowed)

                // Apply Mel filterbank
                val melEnergies = applyMelFilterbank(powerSpectrum)

                // Apply log
                val logMel = FloatArray(melEnergies.size) {
                    ln(maxOf(melEnergies[it], 1e-10f))
                }

                fbankFeatures.add(logMel)
            }

            // Flatten to 1D array
            val totalSize = fbankFeatures.size * NUM_MEL_BINS
            val result = FloatArray(totalSize)
            var idx = 0
            for (frame in fbankFeatures) {
                for (value in frame) {
                    result[idx++] = value
                }
            }

            Log.d(TAG, "Fbank extraction: ${fbankFeatures.size} frames")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error in Fbank extraction", e)
            return FloatArray(0)
        }
    }

    private fun preEmphasis(signal: FloatArray, alpha: Float = 0.97f): FloatArray {
        if (signal.isEmpty()) return signal
        val result = FloatArray(signal.size)
        result[0] = signal[0]
        for (i in 1 until signal.size) {
            result[i] = signal[i] - alpha * signal[i - 1]
        }
        return result
    }

    private fun frameSignal(signal: FloatArray, frameLength: Int, frameShift: Int): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameLength <= signal.size) {
            frames.add(signal.copyOfRange(start, start + frameLength))
            start += frameShift
        }
        return frames
    }

    private fun applyHamming(frame: FloatArray): FloatArray {
        val result = FloatArray(frame.size)
        for (i in frame.indices) {
            val window = 0.54 - 0.46 * cos(2.0 * PI * i / (frame.size - 1))
            result[i] = frame[i] * window.toFloat()
        }
        return result
    }

    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val fftSize = nextPowerOfTwo(frame.size)
        val complex = FloatArray(fftSize * 2)

        // Copy frame data
        for (i in frame.indices) {
            complex[2 * i] = frame[i]
            complex[2 * i + 1] = 0f
        }

        // Perform FFT
        fftRadix2(complex, fftSize)

        // Compute power spectrum
        val power = FloatArray(fftSize / 2 + 1)
        for (i in power.indices) {
            val real = complex[2 * i]
            val imag = complex[2 * i + 1]
            power[i] = real * real + imag * imag
        }
        return power
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) power *= 2
        return power
    }

    private fun fftRadix2(x: FloatArray, n: Int) {
        // Bit reversal
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = x[2 * i]
                x[2 * i] = x[2 * j]
                x[2 * j] = temp

                temp = x[2 * i + 1]
                x[2 * i + 1] = x[2 * j + 1]
                x[2 * j + 1] = temp
            }

            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // FFT computation
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wRealCurrent = 1f
                var wImagCurrent = 0f

                for (j in 0 until len / 2) {
                    val idx1 = 2 * (i + j)
                    val idx2 = 2 * (i + j + len / 2)

                    val real1 = x[idx1]
                    val imag1 = x[idx1 + 1]
                    val real2 = x[idx2]
                    val imag2 = x[idx2 + 1]

                    val tReal = wRealCurrent * real2 - wImagCurrent * imag2
                    val tImag = wRealCurrent * imag2 + wImagCurrent * real2

                    x[idx1] = real1 + tReal
                    x[idx1 + 1] = imag1 + tImag
                    x[idx2] = real1 - tReal
                    x[idx2 + 1] = imag1 - tImag

                    val temp = wRealCurrent
                    wRealCurrent = temp * wReal - wImagCurrent * wImag
                    wImagCurrent = temp * wImag + wImagCurrent * wReal
                }
                i += len
            }
            len *= 2
        }
    }

    private fun applyMelFilterbank(powerSpectrum: FloatArray): FloatArray {
        val lowMel = hzToMel(0f)
        val highMel = hzToMel(SAMPLE_RATE / 2f)

        val melPoints = FloatArray(NUM_MEL_BINS + 2) {
            lowMel + (it.toFloat() / (NUM_MEL_BINS + 1)) * (highMel - lowMel)
        }
        val hzPoints = melPoints.map { melToHz(it) }.toFloatArray()

        val fftSize = (powerSpectrum.size - 1) * 2
        val bins = hzPoints.map {
            ((fftSize + 1) * it / SAMPLE_RATE).toInt().coerceIn(0, powerSpectrum.size - 1)
        }.toIntArray()

        val fbank = FloatArray(NUM_MEL_BINS)

        for (m in 1..NUM_MEL_BINS) {
            val f_m_minus = bins[m - 1]
            val f_m = bins[m]
            val f_m_plus = bins[m + 1]

            var sum = 0f

            for (k in f_m_minus until f_m) {
                val weight = (k - f_m_minus).toFloat() / maxOf(1, f_m - f_m_minus)
                sum += weight * powerSpectrum.getOrElse(k) { 0f }
            }

            for (k in f_m until f_m_plus) {
                val weight = (f_m_plus - k).toFloat() / maxOf(1, f_m_plus - f_m)
                sum += weight * powerSpectrum.getOrElse(k) { 0f }
            }

            fbank[m - 1] = sum
        }

        return fbank
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float) = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var norm = 0f
        for (v in vec) {
            norm += v * v
        }
        norm = sqrt(norm)

        if (norm < 1e-12f) return vec

        return FloatArray(vec.size) { vec[it] / norm }
    }

    @Synchronized
    fun close() {
        if (isClosed) return

        isClosed = true
        try {
            ortSession?.close()
            ortSession = null
            ortEnv?.close()
            ortEnv = null
            Log.d(TAG, "WeSpeaker resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}