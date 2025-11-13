package com.echosense.audio

import kotlin.math.*

class SpeakerDiarizer(private val maxSpeakers: Int = 4) {
    
    private val audioProcessor = AudioProcessor()
    private val speakerProfiles = mutableListOf<SpeakerProfile>()
    private var currentSpeaker: Int? = null
    private val similarityThreshold = 0.7f
    
    data class SpeakerProfile(
        val id: Int,
        val features: MutableList<FloatArray> = mutableListOf(),
        var lastActiveTime: Long = 0
    )
    
    fun processAudioSegment(audioData: ShortArray, sampleRate: Int, timestamp: Long): Int? {
        // Extract features from this audio segment
        val features = audioProcessor.extractMFCC(audioData, sampleRate)
        
        // Find the most similar speaker
        val speakerId = identifySpeaker(features, timestamp)
        
        currentSpeaker = speakerId
        return speakerId
    }
    
    private fun identifySpeaker(features: FloatArray, timestamp: Long): Int {
        if (speakerProfiles.isEmpty()) {
            // First speaker
            val profile = SpeakerProfile(id = 0, lastActiveTime = timestamp)
            profile.features.add(features)
            speakerProfiles.add(profile)
            return 0
        }
        
        // Calculate similarity with existing speakers
        var maxSimilarity = 0f
        var mostSimilarSpeaker = -1
        
        for (profile in speakerProfiles) {
            val avgFeatures = averageFeatures(profile.features)
            val similarity = cosineSimilarity(features, avgFeatures)
            
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                mostSimilarSpeaker = profile.id
            }
        }
        
        // If similarity is high enough, assign to existing speaker
        if (maxSimilarity >= similarityThreshold && mostSimilarSpeaker >= 0) {
            val profile = speakerProfiles.find { it.id == mostSimilarSpeaker }
            profile?.features?.add(features)
            profile?.lastActiveTime = timestamp
            
            // Keep only recent features (max 100)
            if (profile != null && profile.features.size > 100) {
                profile.features.removeAt(0)
            }
            
            return mostSimilarSpeaker
        }
        
        // Create new speaker if under limit
        if (speakerProfiles.size < maxSpeakers) {
            val newId = speakerProfiles.size
            val profile = SpeakerProfile(id = newId, lastActiveTime = timestamp)
            profile.features.add(features)
            speakerProfiles.add(profile)
            return newId
        }
        
        // Assign to closest speaker if at max
        return mostSimilarSpeaker
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA * normB)
        return if (denominator == 0.0) 0f else (dotProduct / denominator).toFloat()
    }
    
    private fun averageFeatures(features: List<FloatArray>): FloatArray {
        if (features.isEmpty()) return FloatArray(0)
        
        val avg = FloatArray(features[0].size)
        for (feature in features) {
            for (i in feature.indices) {
                avg[i] += feature[i]
            }
        }
        
        for (i in avg.indices) {
            avg[i] /= features.size
        }
        
        return avg
    }
    
    fun getSpeakerCount() = speakerProfiles.size
    
    fun reset() {
        speakerProfiles.clear()
        currentSpeaker = null
    }
}