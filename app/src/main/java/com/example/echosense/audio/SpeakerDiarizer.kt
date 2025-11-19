//
//
//
//package com.echosense.audio
//
//import android.util.Log
//import kotlin.math.*
//import java.util.concurrent.atomic.AtomicInteger
//
///**
// * Improved Speaker Diarizer – FINAL FIXED VERSION
// */
//class SpeakerDiarizer(
//    private val maxSpeakers: Int = 4,
//    private var similarityThreshold: Float = 0.60f, // tune 0.55–0.70
//    private val minSpeechAmplitude: Float = 0.01f,
//    private val minExamplesToMatch: Int = 2,
//    private val embeddingWindowSize: Int = 10,
//    private val warmupFramesBeforeCreate: Int = 2,
//    private val minSpeakerChangeInterval: Long = 1500L
//) {
//
//    private val audioProcessor = AudioProcessor()
//    private val speakerProfiles = mutableListOf<SpeakerProfile>()
//
//    private var currentSpeaker: Int? = null
//    private var lastSpeakerChangeTime = 0L
//    private val profileIdCounter = AtomicInteger(0)
//
//    data class SpeakerProfile(
//        val id: Int,
//        val features: MutableList<FloatArray> = mutableListOf(),
//        var lastActiveTime: Long = 0,
//        var totalSamples: Int = 0,
//        var pendingWarmup: Int = 0
//    )
//
//    // -------------------------------------------------------------------
//
//    @Synchronized
//    fun processAudioSegment(audioData: ShortArray, sampleRate: Int, timestamp: Long): Int? {
//        try {
//            // --- Voice Activity Detection ---
//            if (!isSpeech(audioData)) {
//                Log.d(TAG, "VAD: silence → stay at current=$currentSpeaker")
//                return currentSpeaker
//            }
//
//            val rawMFCC = audioProcessor.extractEnhancedMFCC(audioData, sampleRate)
//            if (rawMFCC.isEmpty()) {
//                Log.d(TAG, "DIAR: No MFCC extracted.")
//                return currentSpeaker
//            }
//
//            val features = l2NormalizeCopy(rawMFCC)
//
//            val decidedSpeaker = identifySpeaker(features, timestamp)
//
//            if (decidedSpeaker != null && decidedSpeaker != currentSpeaker) {
//                val now = System.currentTimeMillis()
//                if (now - lastSpeakerChangeTime >= minSpeakerChangeInterval) {
//                    Log.d(TAG, "DIAR: SWITCH → $currentSpeaker → $decidedSpeaker")
//                    currentSpeaker = decidedSpeaker
//                    lastSpeakerChangeTime = now
//                } else {
//                    Log.d(TAG, "DIAR: Switch blocked (interval).")
//                }
//            }
//
//            currentSpeaker?.let { id ->
//                speakerProfiles.find { it.id == id }?.lastActiveTime = timestamp
//            }
//
//            return currentSpeaker
//
//        } catch (e: Exception) {
//            Log.e(TAG, "processAudioSegment ERROR", e)
//            return currentSpeaker
//        }
//    }
//
//    // -------------------------------------------------------------------
//
//    @Synchronized
//    fun getSpeakerCount(): Int =
//        speakerProfiles.count { it.totalSamples >= minExamplesToMatch }
//
//    @Synchronized
//    fun getActiveSpeakers(): List<Int> =
//        speakerProfiles.map { it.id }
//
//    @Synchronized
//    fun reset() {
//        speakerProfiles.clear()
//        currentSpeaker = null
//        lastSpeakerChangeTime = 0L
//        profileIdCounter.set(0)
//        Log.d(TAG, "SpeakerDiarizer RESET")
//    }
//
//    // -------------------------------------------------------------------
//    // INTERNAL LOGIC
//    // -------------------------------------------------------------------
//
//    private fun isSpeech(audio: ShortArray): Boolean {
//        var sumSq = 0.0
//        for (s in audio) sumSq += s * s
//        val rms = sqrt(sumSq / audio.size) / 32768.0
//        return rms > minSpeechAmplitude
//    }
//
//    private fun identifySpeaker(features: FloatArray, timestamp: Long): Int? {
//        Log.d(TAG, "--- identifySpeaker(), profiles=${speakerProfiles.size} ---")
//
//        // First ever speaker
//        if (speakerProfiles.isEmpty()) {
//            return createWarmupSpeaker(features, timestamp)
//        }
//
//        // Compare against existing profiles
//        var bestId = -1
//        var bestSim = -2f
//
//        for (profile in speakerProfiles) {
//            if (profile.features.size < minExamplesToMatch) continue
//
//            val avg = averageFeatures(profile.features)
//            val sim = cosineSimilarity(features, avg)
//
//            Log.d(TAG, "DIAR: similarity with speaker ${profile.id} = $sim")
//
//            if (sim > bestSim) {
//                bestSim = sim
//                bestId = profile.id
//            }
//        }
//
//        Log.d(TAG, "DIAR: BEST=$bestId, SIM=$bestSim, THRESHOLD=$similarityThreshold")
//
//        // Assign to existing speaker
//        if (bestId != -1 && bestSim >= similarityThreshold) {
//            updateSpeakerProfile(bestId, features, timestamp)
//            return bestId
//        }
//
//        // Create new speaker if allowed
//        if (speakerProfiles.size < maxSpeakers) {
//            return createWarmupSpeaker(features, timestamp)
//        }
//
//        // FINAL FALLBACK (FIXED!)
//        val fallback = speakerProfiles.maxByOrNull { it.totalSamples }
//        fallback?.let {
//            Log.d(TAG, "Fallback to speaker ${it.id}")
//            updateSpeakerProfile(it.id, features, timestamp)
//            return it.id
//        }
//
//        return currentSpeaker
//    }
//
//    // -------------------------------------------------------------------
//
//    private fun createWarmupSpeaker(features: FloatArray, timestamp: Long): Int {
//        val last = speakerProfiles.lastOrNull()
//
//        if (last != null && last.totalSamples == 0 && last.pendingWarmup > 0) {
//            last.features.add(features)
//            last.pendingWarmup++
//
//            Log.d(TAG, "Warmup speaker ${last.id}: ${last.pendingWarmup}/$warmupFramesBeforeCreate")
//
//            if (last.pendingWarmup >= warmupFramesBeforeCreate) {
//                last.totalSamples = last.features.size
//                last.lastActiveTime = timestamp
//                Log.d(TAG, "Warmup COMPLETE → Speaker ${last.id} created.")
//                return last.id
//            }
//            return last.id
//        }
//
//        // New profile
//        val id = profileIdCounter.getAndIncrement()
//        val profile = SpeakerProfile(id = id, lastActiveTime = timestamp, totalSamples = 0, pendingWarmup = 1)
//        profile.features.add(features)
//
//        speakerProfiles.add(profile)
//        Log.d(TAG, "Created new pending speaker: $id")
//
//        if (warmupFramesBeforeCreate == 1) {
//            profile.totalSamples = 1
//            Log.d(TAG, "Speaker $id confirmed immediately.")
//        }
//
//        return id
//    }
//
//    // -------------------------------------------------------------------
//
//    private fun updateSpeakerProfile(id: Int, features: FloatArray, timestamp: Long) {
//        val p = speakerProfiles.find { it.id == id } ?: return
//
//        p.features.add(features)
//        p.totalSamples++
//        p.lastActiveTime = timestamp
//        p.pendingWarmup = 0
//
//        if (p.features.size > embeddingWindowSize) {
//            p.features.removeAt(0)
//        }
//
//        Log.d(TAG, "Updated speaker $id (samples=${p.totalSamples})")
//    }
//
//    // -------------------------------------------------------------------
//
//    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
//        var dot = 0f
//        var na = 0f
//        var nb = 0f
//
//        for (i in a.indices) {
//            dot += a[i] * b[i]
//            na += a[i] * a[i]
//            nb += b[i] * b[i]
//        }
//
//        val denom = sqrt(na * nb)
//        if (denom == 0f) return -1f
//        return (dot / denom).coerceIn(-1f, 1f)
//    }
//
//    private fun l2NormalizeCopy(v: FloatArray): FloatArray {
//        val out = v.copyOf()
//        var sum = 0f
//        for (x in out) sum += x * x
//        val norm = sqrt(sum)
//        if (norm < 1e-9) return out
//        for (i in out.indices) out[i] /= norm
//        return out
//    }
//
//    private fun averageFeatures(list: List<FloatArray>): FloatArray {
//        val size = list[0].size
//        val avg = FloatArray(size)
//
//        for (v in list) {
//            for (i in 0 until size) avg[i] += v[i]
//        }
//        for (i in 0 until size) avg[i] /= list.size
//
//        return l2NormalizeCopy(avg)
//    }
//
//    companion object {
//        private const val TAG = "SpeakerDiarizer"
//    }
//}


package com.echosense.audio

import android.util.Log
import kotlin.math.*

class SpeakerDiarizer(
    private val maxSpeakers: Int = 4,
    private val similarityThreshold: Float = 0.72f,  // tuned for 39-dim MFCC
    private val windowSize: Int = 10
) {

    private val profiles = mutableListOf<SpeakerProfile>()
    private var currentSpeaker: Int? = null
    private var lastSwitchTime = 0L

    private val minSwitchDelay = 800L // ms

    data class SpeakerProfile(
        val id: Int,
        val embeddings: MutableList<FloatArray> = mutableListOf()
    )

    fun reset() {
        profiles.clear()
        currentSpeaker = null
    }

    fun process(features: FloatArray, timestamp: Long): Int? {
        if (features.isEmpty()) return currentSpeaker

        val normalized = l2norm(features)

        if (profiles.isEmpty()) {
            return createSpeaker(normalized)
        }

        // find best profile
        var bestId = -1
        var bestSim = -2f

        for (p in profiles) {
            val centroid = computeCentroid(p.embeddings)
            val sim = cosine(centroid, normalized)

            Log.d("DIAR", "Speaker ${p.id+1} sim: $sim")

            if (sim > bestSim) {
                bestSim = sim
                bestId = p.id
            }
        }

        Log.d("DIAR", "Best sim=$bestSim (th=$similarityThreshold)")

        // matched?
        if (bestSim >= similarityThreshold) {
            updateSpeaker(bestId, normalized)
            trySwitch(bestId, timestamp)
            return currentSpeaker
        }

        // new speaker?
        if (profiles.size < maxSpeakers) {
            val newId = createSpeaker(normalized)
            trySwitch(newId, timestamp)
            return newId
        }

        // fallback: use best match anyway
        updateSpeaker(bestId, normalized)
        trySwitch(bestId, timestamp)
        return bestId
    }

    private fun trySwitch(id: Int, timestamp: Long) {
        val now = timestamp
        if (currentSpeaker == null || currentSpeaker != id) {
            if (now - lastSwitchTime > minSwitchDelay) {
                currentSpeaker = id
                lastSwitchTime = now
                Log.d("DIAR", "Switched → Speaker ${id+1}")
            }
        }
    }

    private fun createSpeaker(vec: FloatArray): Int {
        val newId = profiles.size
        val sp = SpeakerProfile(newId)
        sp.embeddings.add(vec)
        profiles.add(sp)

        Log.d("DIAR", "New speaker created: ${newId+1}")
        return newId
    }

    private fun updateSpeaker(id: Int, vec: FloatArray) {
        val p = profiles[id]
        p.embeddings.add(vec)

        if (p.embeddings.size > windowSize)
            p.embeddings.removeAt(0)
    }

    private fun computeCentroid(list: List<FloatArray>): FloatArray {
        val out = FloatArray(list[0].size)
        for (f in list) for (i in f.indices) out[i] += f[i]
        for (i in out.indices) out[i] /= list.size
        return out
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }

        val denom = sqrt(na * nb)
        return if (denom == 0f) -1f else (dot / denom).coerceIn(-1f, 1f)
    }

    private fun l2norm(v: FloatArray): FloatArray {
        val out = v.copyOf()
        var sum = 0f
        for (x in out) sum += x * x
        val n = sqrt(sum)
        if (n < 1e-9f) return out
        for (i in out.indices) out[i] /= n
        return out
    }
}
