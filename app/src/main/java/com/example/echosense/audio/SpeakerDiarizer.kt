//package com.echosense.audio
//
//import android.util.Log
//import kotlin.math.*
//
//class SpeakerDiarizer(
//    private val maxSpeakers: Int = 4,
//    private val similarityThreshold: Float = 0.95f,  // Lower threshold
//    private val windowSize: Int = 15,
//    private val minSwitchDelay: Long = 1000L,  // 1 second
//    private val confirmationFrames: Int = 2
//) {
//
//    private val TAG = "DIAR"
//    private val profiles = mutableListOf<SpeakerProfile>()
//    private var currentSpeaker: Int? = null
//    private var lastSwitchTime = 0L
//
//    private var pendingSpeaker: Int? = null
//    private var pendingConfirmationCount = 0
//
//    // Track frame count to avoid premature initialization
//    private var frameCount = 0
//    private val MIN_FRAMES_BEFORE_INIT = 5
//
//    data class SpeakerProfile(
//        val id: Int,
//        val embeddings: MutableList<FloatArray> = mutableListOf(),
//        var lastActiveTime: Long = 0L,
//        var totalActiveTime: Long = 0L
//    )
//
//    fun reset() {
//        profiles.clear()
//        currentSpeaker = null
//        pendingSpeaker = null
//        pendingConfirmationCount = 0
//        lastSwitchTime = 0L
//        frameCount = 0
//        Log.d(TAG, "🔄 Reset complete")
//    }
//
//    fun process(embedding: FloatArray, timestamp: Long): Int? {
//        if (embedding.isEmpty()) return currentSpeaker
//
//        frameCount++
//
//        // Wait for a few frames before starting diarization
//        if (frameCount < MIN_FRAMES_BEFORE_INIT) {
//            return null
//        }
//
//        // === FIRST SPEAKER INITIALIZATION ===
//        if (profiles.isEmpty()) {
//            val firstId = createSpeaker(embedding, timestamp)
//            currentSpeaker = firstId
//            lastSwitchTime = timestamp
//            Log.d(TAG, "🎉 First speaker S${firstId + 1} initialized")
//            return firstId
//        }
//
//        // === CALCULATE SIMILARITIES ===
//        val similarities = mutableListOf<Pair<Int, Float>>()
//
//        for (profile in profiles) {
//            if (profile.embeddings.isEmpty()) continue
//            val centroid = computeCentroid(profile.embeddings)
//            val sim = cosineSimilarity(embedding, centroid)
//            similarities.add(profile.id to sim)
//        }
//
//        if (similarities.isEmpty()) return currentSpeaker
//
//        // Sort by similarity descending
//        similarities.sortByDescending { it.second }
//
//        val bestMatch = similarities[0]
//        val bestId = bestMatch.first
//        val bestSim = bestMatch.second
//
//        val secondBestSim = similarities.getOrNull(1)?.second ?: -1f
//        val margin = bestSim - secondBestSim
//
//        // Enhanced logging
//        val speakerSims = similarities.take(3).map { "S${it.first + 1}:%.3f".format(it.second) }
//        Log.d(TAG, "📊 Similarities: ${speakerSims.joinToString(", ")}, Margin=%.3f".format(margin))
//
//        // === DECISION LOGIC (FIXED) ===
//        val detectedSpeaker = when {
//            // CASE 1: Very strong match (> 0.80) - almost certainly same speaker
//            bestSim >= 0.80f -> {
//                Log.d(TAG, "✅ Strong match: S${bestId + 1} (sim=%.3f)".format(bestSim))
//                updateSpeaker(bestId, embedding, timestamp)
//                bestId
//            }
//
//            // CASE 2: Good match with clear separation from other speakers
//            bestSim >= similarityThreshold && margin > 0.15f -> {
//                Log.d(TAG, "✅ Clear match: S${bestId + 1} (sim=%.3f, margin=%.3f)".format(bestSim, margin))
//                updateSpeaker(bestId, embedding, timestamp)
//                bestId
//            }
//
//            // CASE 3: Ambiguous - multiple speakers with similar scores
//            // This is the KEY FIX - when margin is small, prefer switching to different speaker
//            bestSim >= similarityThreshold && margin <= 0.15f -> {
//                // If current speaker is in the ambiguous range, try switching
//                val currentSim = similarities.find { it.first == currentSpeaker }?.second ?: -1f
//
//                if (currentSim >= 0.0f && bestSim - currentSim > 0.05f) {
//                    // Best match is noticeably better than current speaker
//                    Log.d(TAG, "🔄 Switching (ambiguous): S${currentSpeaker?.plus(1)} → S${bestId + 1}")
//                    updateSpeaker(bestId, embedding, timestamp)
//                    bestId
//                } else if (currentSpeaker != null) {
//                    // Stay with current speaker during ambiguity
//                    Log.d(TAG, "⏸️ Staying: S${currentSpeaker!! + 1} (ambiguous)")
//                    updateSpeaker(currentSpeaker!!, embedding, timestamp)
//                    currentSpeaker!!
//                } else {
//                    updateSpeaker(bestId, embedding, timestamp)
//                    bestId
//                }
//            }
//
//            // CASE 4: Low similarity - might be new speaker
//            profiles.size < maxSpeakers && bestSim < 0.55f -> {
//                val timeSinceSwitch = timestamp - lastSwitchTime
//                if (timeSinceSwitch > minSwitchDelay) {
//                    val newId = createSpeaker(embedding, timestamp)
//                    Log.d(TAG, "🆕 New speaker S${newId + 1} (sim=%.3f too low)".format(bestSim))
//                    newId
//                } else {
//                    // Too soon to create new speaker, use best match
//                    updateSpeaker(bestId, embedding, timestamp)
//                    bestId
//                }
//            }
//
//            // CASE 5: Fallback to best match
//            else -> {
//                Log.d(TAG, "➡️ Fallback: S${bestId + 1} (sim=%.3f)".format(bestSim))
//                updateSpeaker(bestId, embedding, timestamp)
//                bestId
//            }
//        }
//
//        // === SMOOTHING ===
//        return applySmoothing(detectedSpeaker, timestamp)
//    }
//
//    private fun applySmoothing(detectedSpeaker: Int, timestamp: Long): Int? {
//        // No current speaker
//        if (currentSpeaker == null) {
//            currentSpeaker = detectedSpeaker
//            lastSwitchTime = timestamp
//            Log.d(TAG, "🎤 Started: S${detectedSpeaker + 1}")
//            return detectedSpeaker
//        }
//
//        val current = currentSpeaker!!
//
//        // Same speaker - reset pending
//        if (detectedSpeaker == current) {
//            pendingSpeaker = null
//            pendingConfirmationCount = 0
//            return current
//        }
//
//        // Different speaker detected
//        val timeSinceSwitch = timestamp - lastSwitchTime
//
//        // Too soon to switch
//        if (timeSinceSwitch < minSwitchDelay) {
//            Log.d(TAG, "⏳ Switch blocked: too soon (${timeSinceSwitch}ms)")
//            return current
//        }
//
//        // New pending speaker
//        if (pendingSpeaker != detectedSpeaker) {
//            pendingSpeaker = detectedSpeaker
//            pendingConfirmationCount = 1
//            Log.d(TAG, "🔄 Pending: S${current + 1} → S${detectedSpeaker + 1} (1/$confirmationFrames)")
//            return current
//        }
//
//        // Increment confirmation
//        pendingConfirmationCount++
//
//        // Confirmed switch
//        if (pendingConfirmationCount >= confirmationFrames) {
//            currentSpeaker = detectedSpeaker
//            lastSwitchTime = timestamp
//            pendingSpeaker = null
//            pendingConfirmationCount = 0
//
//            Log.d(TAG, "🎊 SWITCHED: S${current + 1} → S${detectedSpeaker + 1}")
//            return detectedSpeaker
//        }
//
//        // Still confirming
//        Log.d(TAG, "🔄 Confirming: S${current + 1} → S${detectedSpeaker + 1} ($pendingConfirmationCount/$confirmationFrames)")
//        return current
//    }
//
//    private fun createSpeaker(embedding: FloatArray, timestamp: Long): Int {
//        val newId = profiles.size
//        val profile = SpeakerProfile(
//            id = newId,
//            lastActiveTime = timestamp
//        )
//        profile.embeddings.add(embedding)
//        profiles.add(profile)
//        Log.d(TAG, "✨ Created Speaker ${newId + 1}. Total speakers: ${profiles.size}")
//        return newId
//    }
//
//    private fun updateSpeaker(id: Int, embedding: FloatArray, timestamp: Long) {
//        val profile = profiles.getOrNull(id) ?: return
//        profile.embeddings.add(embedding)
//        profile.lastActiveTime = timestamp
//
//        // Maintain sliding window
//        if (profile.embeddings.size > windowSize) {
//            profile.embeddings.removeAt(0)
//        }
//    }
//
//    private fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
//        if (embeddings.isEmpty()) return FloatArray(0)
//
//        val size = embeddings[0].size
//        val centroid = FloatArray(size)
//
//        for (emb in embeddings) {
//            for (i in emb.indices) {
//                centroid[i] += emb[i]
//            }
//        }
//
//        for (i in centroid.indices) {
//            centroid[i] /= embeddings.size
//        }
//
//        return centroid
//    }
//
//    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
//        if (a.size != b.size || a.isEmpty()) return -1f
//
//        var dot = 0f
//        var normA = 0f
//        var normB = 0f
//
//        for (i in a.indices) {
//            dot += a[i] * b[i]
//            normA += a[i] * a[i]
//            normB += b[i] * b[i]
//        }
//
//        val denom = sqrt(normA * normB)
//        return if (denom < 1e-12f) -1f else (dot / denom).coerceIn(-1f, 1f)
//    }
//
//    fun getSpeakerCount(): Int = profiles.size
//
//    fun getDebugInfo(): String {
//        return "Speakers: ${profiles.size}, Current: S${currentSpeaker?.plus(1) ?: "none"}"
//    }
//}

package com.echosense.audio

import android.util.Log
import kotlin.math.sqrt

class SpeakerDiarizer(
    private val maxSpeakers: Int = 6,
    private val similarityThreshold: Float = 0.75f,      // NEW: more sensitive
    private val marginThreshold: Float = 0.10f,          // NEW: detects close voices as new speaker
    private val minSwitchDelayMs: Long = 300             // NEW: much faster switching
) {

    private val TAG = "DIAR"

    private val speakerProfiles = mutableListOf<FloatArray>()
    private var lastSpeaker: Int? = null
    private var lastSwitchTime = 0L

    fun getSpeakerCount(): Int = speakerProfiles.size

    // L2 Normalize embeddings → improves separation
    private fun normalize(e: FloatArray): FloatArray {
        val norm = sqrt(e.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0) FloatArray(e.size) { i -> e[i] / norm } else e
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
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1f, 1f)
    }

    fun process(embeddingRaw: FloatArray, timestampMs: Long): Int? {
        if (embeddingRaw.isEmpty()) return lastSpeaker

        // Normalize for stability
        val embedding = normalize(embeddingRaw)

        // If no profiles → first speaker
        if (speakerProfiles.isEmpty()) {
            speakerProfiles.add(embedding)
            lastSpeaker = 0
            lastSwitchTime = timestampMs
            Log.d(TAG, "🆕 Created Speaker 1 (initial)")
            return 0
        }

        // Calculate similarities to all known speakers
        val similarities = speakerProfiles.map { cosine(embedding, it) }
        val bestSim = similarities.maxOrNull() ?: -1f
        val bestIndex = similarities.indexOf(bestSim)

        Log.d(TAG, "📊 Similarities: " + similarities.map { "%.3f".format(it) })

        val margin = 1 - bestSim
        Log.d(TAG, "📐 Margin = %.3f".format(margin))

        // --- NEW SPEAKER CREATION RULES ---
        val shouldCreateNew =
            bestSim < similarityThreshold ||       // not similar enough
                    margin < marginThreshold ||            // too close → probably different
                    (lastSpeaker != null &&
                            bestIndex != lastSpeaker &&
                            bestSim - (similarities[lastSpeaker!!]) < 0.12f)  // difference spike

        // --- RATE LIMIT SWITCHING ---
        val elapsed = timestampMs - lastSwitchTime
        val canSwitch = elapsed > minSwitchDelayMs

        if (shouldCreateNew && speakerProfiles.size < maxSpeakers) {
            speakerProfiles.add(embedding)
            val newId = speakerProfiles.lastIndex
            lastSpeaker = newId
            lastSwitchTime = timestampMs
            Log.d(TAG, "🆕 Created Speaker ${newId + 1}")
            return newId
        }

        if (!canSwitch) {
            return lastSpeaker
        }

        // Assign to best matching speaker
        lastSpeaker = bestIndex
        lastSwitchTime = timestampMs

        Log.d(TAG, "🎯 Strong match → Speaker ${bestIndex + 1}")
        return bestIndex
    }
}
