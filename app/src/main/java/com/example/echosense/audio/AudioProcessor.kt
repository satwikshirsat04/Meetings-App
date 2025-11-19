package com.echosense.audio

import kotlin.math.*

class AudioProcessor {

    companion object {
        private const val FFT_SIZE = 512
        private const val NUM_MFCC = 13
        private const val NUM_FILTERS = 26
        private const val FRAME_SIZE = 400  // Larger than incoming 320 samples
        private const val HOP_SIZE = 160     // 50% overlap
    }

    // Buffer to accumulate audio samples
    private val audioBuffer = mutableListOf<Float>()
    private val MAX_BUFFER_SIZE = 5000  // Keep last 5000 samples

    // -------- PUBLIC API ----------
    fun extractEnhancedMFCC(audioData: ShortArray, sampleRate: Int): FloatArray {
        // Convert to float and add to buffer
        val floatData = FloatArray(audioData.size) { audioData[it] / 32768f }

        synchronized(audioBuffer) {
            audioBuffer.addAll(floatData.toList())

            // Keep buffer size manageable
            if (audioBuffer.size > MAX_BUFFER_SIZE) {
                audioBuffer.subList(0, audioBuffer.size - MAX_BUFFER_SIZE).clear()
            }

            // Need at least FFT_SIZE samples to process
            if (audioBuffer.size < FFT_SIZE) {
                return FloatArray(0)
            }

            // Get data from buffer
            val bufferArray = audioBuffer.toFloatArray()

            val emphasized = preEmphasis(bufferArray)
            val frames = frameSignal(emphasized, FRAME_SIZE, HOP_SIZE)

            if (frames.size < 2) {
                return FloatArray(0)
            }

            val mfccList = frames.map { frame ->
                val padded = padToSize(frame, FFT_SIZE)
                val windowed = applyHamming(padded)
                val power = computePowerSpectrum(windowed)
                computeMFCC(power, sampleRate, NUM_MFCC)
            }

            if (mfccList.isEmpty()) {
                return FloatArray(0)
            }

            val mfcc = averageFrames(mfccList)
            val deltas = computeDeltaCoefficients(mfccList)
            val deltaDelta = if (mfccList.size >= 3) {
                computeDeltaCoefficients(listOf(mfccList[0], deltas, mfccList.last()))
            } else {
                FloatArray(NUM_MFCC) { 0f }
            }

            // Concatenate: MFCC + Delta + Delta-Delta = 13 + 13 + 13 = 39
            return mfcc + deltas + deltaDelta
        }
    }

    // Pad frame to required size
    private fun padToSize(frame: FloatArray, targetSize: Int): FloatArray {
        if (frame.size >= targetSize) {
            return frame.copyOf(targetSize)
        }
        val padded = FloatArray(targetSize)
        frame.copyInto(padded)
        return padded
    }

    // -------- PRE-EMPHASIS ----------
    private fun preEmphasis(data: FloatArray, alpha: Float = 0.97f): FloatArray {
        if (data.isEmpty()) return data
        val out = FloatArray(data.size)
        out[0] = data[0]
        for (i in 1 until data.size)
            out[i] = data[i] - alpha * data[i - 1]
        return out
    }

    // -------- FRAME SIGNAL ----------
    private fun frameSignal(signal: FloatArray, frameSize: Int, hop: Int): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameSize <= signal.size) {
            frames.add(signal.copyOfRange(start, start + frameSize))
            start += hop
        }
        return frames
    }

    // -------- HAMMING WINDOW ----------
    private fun applyHamming(frame: FloatArray): FloatArray {
        val out = FloatArray(frame.size)
        for (i in frame.indices) {
            val window = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (frame.size - 1))
            out[i] = frame[i] * window.toFloat()
        }
        return out
    }

    // -------- POWER SPECTRUM ----------
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val complex = FloatArray(FFT_SIZE * 2)

        // Copy frame data
        for (i in 0 until minOf(frame.size, FFT_SIZE)) {
            complex[2 * i] = frame[i]
            complex[2 * i + 1] = 0f
        }

        // Perform FFT
        fftRadix2(complex, FFT_SIZE)

        // Compute power spectrum
        val power = FloatArray(FFT_SIZE / 2 + 1)
        for (i in power.indices) {
            val real = complex[2 * i]
            val imag = complex[2 * i + 1]
            power[i] = (real * real + imag * imag)
        }
        return power
    }

    // Simplified iterative FFT
    private fun fftRadix2(x: FloatArray, n: Int) {
        // Bit reversal
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                // Swap real and imaginary parts
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
            val angle = -2.0 * Math.PI / len
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

    // -------- MFCC COMPUTATION ----------
    private fun computeMFCC(powerSpectrum: FloatArray, sampleRate: Int, numCoeffs: Int): FloatArray {
        val filterbank = melFilterbank(powerSpectrum, sampleRate)
        val logEnergies = filterbank.map { ln(max(it, 1e-10f)) }.toFloatArray()

        val mfcc = FloatArray(numCoeffs)
        val M = logEnergies.size

        for (i in 0 until numCoeffs) {
            var sum = 0f
            for (j in 0 until M) {
                val angle = Math.PI * i * (j + 0.5) / M
                sum += logEnergies[j] * cos(angle).toFloat()
            }
            mfcc[i] = sum
        }
        return mfcc
    }

    // -------- MEL FILTERBANK ----------
    private fun melFilterbank(powerSpectrum: FloatArray, sampleRate: Int): FloatArray {
        val lowMel = hzToMel(0f)
        val highMel = hzToMel(sampleRate / 2f)

        val melPoints = FloatArray(NUM_FILTERS + 2) {
            lowMel + (it.toFloat() / (NUM_FILTERS + 1)) * (highMel - lowMel)
        }
        val hzPoints = melPoints.map { melToHz(it) }.toFloatArray()
        val bins = hzPoints.map { ((FFT_SIZE + 1) * it / sampleRate).toInt().coerceIn(0, FFT_SIZE / 2) }.toIntArray()

        val fbanks = FloatArray(NUM_FILTERS)

        for (m in 1..NUM_FILTERS) {
            val f_m_minus = bins[m - 1]
            val f_m = bins[m]
            val f_m_plus = bins[m + 1]

            var sum = 0f

            // Rising slope
            for (k in f_m_minus until f_m) {
                val weight = (k - f_m_minus).toFloat() / maxOf(1, f_m - f_m_minus)
                sum += weight * powerSpectrum.getOrElse(k) { 0f }
            }

            // Falling slope
            for (k in f_m until f_m_plus) {
                val weight = (f_m_plus - k).toFloat() / maxOf(1, f_m_plus - f_m)
                sum += weight * powerSpectrum.getOrElse(k) { 0f }
            }

            fbanks[m - 1] = sum
        }

        return fbanks
    }

    // -------- DELTA COEFFICIENTS ----------
    private fun computeDeltaCoefficients(frames: List<FloatArray>): FloatArray {
        val M = frames.size
        if (M == 0) return FloatArray(0)

        val N = frames[0].size
        val out = FloatArray(N)

        if (M < 2) {
            return out
        }

        // Use first and last frame for delta
        for (n in 0 until N) {
            out[n] = (frames[M - 1][n] - frames[0][n]) / maxOf(1, M - 1)
        }
        return out
    }

    // -------- AVERAGE FRAMES ----------
    private fun averageFrames(frames: List<FloatArray>): FloatArray {
        if (frames.isEmpty()) return FloatArray(0)

        val out = FloatArray(frames[0].size)
        for (f in frames) {
            for (i in f.indices) {
                out[i] += f[i]
            }
        }
        for (i in out.indices) {
            out[i] /= frames.size
        }
        return out
    }

    // -------- MEL SCALE CONVERSIONS ----------
    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float) = 700f * (10f.pow(mel / 2595f) - 1f)

    // Reset buffer when needed
    fun reset() {
        synchronized(audioBuffer) {
            audioBuffer.clear()
        }
    }
}