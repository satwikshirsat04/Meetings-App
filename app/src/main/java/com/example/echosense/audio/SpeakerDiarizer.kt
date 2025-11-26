
package com.echosense.audio

import android.util.Log
import kotlin.math.sqrt

/**
 * Simple ECAPA-TDNN based speaker diarizer.
 *
 * - Uses L2-normalized embeddings from WeSpeakerEmbedding.
 * - Uses cosine similarity to match against existing speakers.
 * - Creates a NEW speaker **only when** similarity is below a threshold.
 *
 * IMPORTANT FIX:
 * We no longer use `margin = 1 - bestSim` or treat “very high similarity” as new speaker.
 * High similarity => same speaker. Low similarity => possible new speaker.
 */
class SpeakerDiarizer(
    private val maxSpeakers: Int = 6,
    private val similarityThreshold: Float = 0.75f,  // if bestSim < this => new speaker
    private val minSwitchDelayMs: Long = 300L        // minimum ms between speaker switches
) {

    private val TAG = "DIAR"

    // One centroid-like embedding per speaker
    private val speakerProfiles = mutableListOf<FloatArray>()

    // Index of last chosen speaker (0-based)
    private var lastSpeaker: Int? = null

    // Timestamp of last switch/creation in ms
    private var lastSwitchTime: Long = 0L

    fun getSpeakerCount(): Int = speakerProfiles.size

    fun reset() {
        speakerProfiles.clear()
        lastSpeaker = null
        lastSwitchTime = 0L
        Log.d(TAG, "🔄 SpeakerDiarizer reset")
    }

    // L2 normalize embeddings → improves cosine stability
    private fun normalize(e: FloatArray): FloatArray {
        var norm = 0f
        for (v in e) {
            norm += v * v
        }
        norm = sqrt(norm)

        return if (norm > 1e-12f) {
            FloatArray(e.size) { i -> e[i] / norm }
        } else {
            e
        }
    }

    // Cosine similarity between two vectors
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f

        var dot = 0f
        var na = 0f
        var nb = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }

        val denom = sqrt(na) * sqrt(nb)
        if (denom < 1e-12f) return -1f

        return (dot / denom).coerceIn(-1f, 1f)
    }

    /**
     * Process a new ECAPA embedding and return the active speaker index (0-based),
     * or null if we keep the previous one and don’t want to change UI yet.
     *
     * @param embeddingRaw 192-dim (or similar) ECAPA-TDNN embedding
     * @param timestampMs  time from start in ms (same as LiveCaptureViewModel’s timeFromStart)
     */
    fun process(embeddingRaw: FloatArray, timestampMs: Long): Int? {
        if (embeddingRaw.isEmpty()) return lastSpeaker

        // Normalize for stability
        val embedding = normalize(embeddingRaw)

        // === FIRST SPEAKER INITIALIZATION ===
        if (speakerProfiles.isEmpty()) {
            speakerProfiles.add(embedding)
            lastSpeaker = 0
            lastSwitchTime = timestampMs
            Log.d(TAG, "🆕 Created Speaker 1 (initial)")
            return 0
        }

        // === CALCULATE SIMILARITIES ===
        val similarities = speakerProfiles.map { cosine(embedding, it) }
        val bestSim = similarities.maxOrNull() ?: -1f
        val bestIndex = similarities.indexOf(bestSim).coerceAtLeast(0)

        // Debug log
        val formatted = similarities.mapIndexed { index, sim ->
            "S${index + 1}:%.3f".format(sim)
        }
        Log.d(
            TAG,
            "📊 Similarities: ${formatted.joinToString(", ")}, " +
                    "best=S${bestIndex + 1}, sim=%.3f".format(bestSim)
        )

        // === DECIDE: NEW SPEAKER OR EXISTING ONE? ===

        // Only create new speaker if similarity is low enough
        val shouldCreateNew =
            bestSim < similarityThreshold && speakerProfiles.size < maxSpeakers

        val elapsed = timestampMs - lastSwitchTime
        val canSwitch = elapsed >= minSwitchDelayMs

        if (shouldCreateNew) {
            // Low similarity to all known speakers → new speaker
            speakerProfiles.add(embedding)
            val newId = speakerProfiles.lastIndex
            lastSpeaker = newId
            lastSwitchTime = timestampMs
            Log.d(
                TAG,
                "🆕 Created Speaker ${newId + 1} (bestSim=%.3f < %.2f)"
                    .format(bestSim, similarityThreshold)
            )
            return newId
        }

        // Don’t switch speakers too frequently
        if (!canSwitch) {
            val stayId = lastSpeaker ?: bestIndex
            Log.d(
                TAG,
                "⏳ Switch blocked: too soon (${elapsed}ms). Staying on S${stayId + 1}"
            )
            lastSpeaker = stayId
            return stayId
        }

        // Assign to best matching existing speaker
        lastSpeaker = bestIndex
        lastSwitchTime = timestampMs

        Log.d(TAG, "🎯 Assigned to Speaker ${bestIndex + 1} (bestSim=%.3f)".format(bestSim))
        return bestIndex
    }
}
