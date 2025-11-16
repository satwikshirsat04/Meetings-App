//package com.echosense.audio
//
//import android.util.Log
//import kotlin.math.*
//
//class SpeakerDiarizer(private val maxSpeakers: Int = 4) {
//
//    private val audioProcessor = AudioProcessor()
//    private val speakerProfiles = mutableListOf<SpeakerProfile>()
//    private var currentSpeaker: Int? = null
//    private val similarityThreshold = 0.75f
//    private val minSamplesForNewSpeaker = 3
//    private val pendingSamples = mutableMapOf<Int, MutableList<FloatArray>>()
//
//    data class SpeakerProfile(
//        val id: Int,
//        val features: MutableList<FloatArray> = mutableListOf(),
//        var lastActiveTime: Long = 0,
//        var totalSamples: Int = 0
//    )
//
//    fun processAudioSegment(audioData: ShortArray, sampleRate: Int, timestamp: Long): Int? {
//        try {
//            // Extract features from this audio segment
//            val features = audioProcessor.extractMFCC(audioData, sampleRate)
//
//            // Validate features
//            if (features.isEmpty() || features.all { it == 0f }) {
//                Log.w("SpeakerDiarizer", "Invalid features extracted")
//                return currentSpeaker
//            }
//
//            // Find the most similar speaker
//            val speakerId = identifySpeaker(features, timestamp)
//
//            currentSpeaker = speakerId
//            Log.d("SpeakerDiarizer", "Identified speaker: ${speakerId + 1}, Total speakers: ${speakerProfiles.size}")
//            return speakerId
//
//        } catch (e: Exception) {
//            Log.e("SpeakerDiarizer", "Error processing audio segment", e)
//            return currentSpeaker
//        }
//    }
//
//    private fun identifySpeaker(features: FloatArray, timestamp: Long): Int {
//        if (speakerProfiles.isEmpty()) {
//            // First speaker
//            val profile = SpeakerProfile(id = 0, lastActiveTime = timestamp, totalSamples = 1)
//            profile.features.add(features)
//            speakerProfiles.add(profile)
//            Log.d("SpeakerDiarizer", "Created first speaker (Speaker 1)")
//            return 0
//        }
//
//        // Calculate similarity with existing speakers
//        var maxSimilarity = 0f
//        var mostSimilarSpeaker = -1
//        val similarities = mutableListOf<Pair<Int, Float>>()
//
//        for (profile in speakerProfiles) {
//            val avgFeatures = averageFeatures(profile.features)
//            val similarity = cosineSimilarity(features, avgFeatures)
//            similarities.add(Pair(profile.id, similarity))
//
//            Log.d("SpeakerDiarizer", "Similarity with Speaker ${profile.id + 1}: $similarity")
//
//            if (similarity > maxSimilarity) {
//                maxSimilarity = similarity
//                mostSimilarSpeaker = profile.id
//            }
//        }
//
//        // If similarity is high enough, assign to existing speaker
//        if (maxSimilarity >= similarityThreshold && mostSimilarSpeaker >= 0) {
//            val profile = speakerProfiles.find { it.id == mostSimilarSpeaker }
//            profile?.features?.add(features)
//            profile?.lastActiveTime = timestamp
//            profile?.totalSamples = (profile?.totalSamples ?: 0) + 1
//
//            // Keep only recent features (max 50 for better memory management)
//            if (profile != null && profile.features.size > 50) {
//                profile.features.removeAt(0)
//            }
//
//            Log.d("SpeakerDiarizer", "Matched to Speaker ${mostSimilarSpeaker + 1} (similarity: $maxSimilarity)")
//            return mostSimilarSpeaker
//        }
//
//        // Check if we should create a new speaker
//        if (speakerProfiles.size < maxSpeakers) {
//            // Add to pending samples
//            val pendingKey = pendingSamples.keys.maxOrNull()?.plus(1) ?: 0
//            if (!pendingSamples.containsKey(pendingKey)) {
//                pendingSamples[pendingKey] = mutableListOf()
//            }
//            pendingSamples[pendingKey]?.add(features)
//
//            // If we have enough pending samples, create new speaker
//            if (pendingSamples[pendingKey]?.size ?: 0 >= minSamplesForNewSpeaker) {
//                val newId = speakerProfiles.size
//                val profile = SpeakerProfile(id = newId, lastActiveTime = timestamp, totalSamples = 1)
//
//                // Add all pending samples to new profile
//                pendingSamples[pendingKey]?.forEach { profile.features.add(it) }
//                profile.totalSamples = profile.features.size
//
//                speakerProfiles.add(profile)
//                pendingSamples.remove(pendingKey)
//
//                Log.d("SpeakerDiarizer", "Created new speaker (Speaker ${newId + 1})")
//                return newId
//            }
//
//            // Not enough samples yet, assign to most similar
//            Log.d("SpeakerDiarizer", "Pending new speaker, temporarily assigned to Speaker ${mostSimilarSpeaker + 1}")
//            return if (mostSimilarSpeaker >= 0) mostSimilarSpeaker else 0
//        }
//
//        // At max speakers, assign to closest match
//        Log.d("SpeakerDiarizer", "Max speakers reached, assigning to Speaker ${mostSimilarSpeaker + 1}")
//        return if (mostSimilarSpeaker >= 0) mostSimilarSpeaker else 0
//    }
//
//    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
//        if (a.size != b.size) return 0f
//
//        var dotProduct = 0.0
//        var normA = 0.0
//        var normB = 0.0
//
//        for (i in a.indices) {
//            dotProduct += a[i] * b[i]
//            normA += a[i] * a[i]
//            normB += b[i] * b[i]
//        }
//
//        val denominator = sqrt(normA * normB)
//        return if (denominator == 0.0) 0f else (dotProduct / denominator).toFloat()
//    }
//
//    private fun averageFeatures(features: List<FloatArray>): FloatArray {
//        if (features.isEmpty()) return FloatArray(0)
//
//        val avg = FloatArray(features[0].size)
//        for (feature in features) {
//            for (i in feature.indices) {
//                avg[i] += feature[i]
//            }
//        }
//
//        for (i in avg.indices) {
//            avg[i] /= features.size
//        }
//
//        return avg
//    }
//
//    fun getSpeakerCount() = speakerProfiles.size
//
//    fun reset() {
//        speakerProfiles.clear()
//        pendingSamples.clear()
//        currentSpeaker = null
//        Log.d("SpeakerDiarizer", "Reset speaker diarization")
//    }
//}