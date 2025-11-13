package com.echosense.ml

class KeywordExtractor {
    
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "will", "with", "i", "you", "we", "they", "this",
        "but", "or", "not", "so", "if", "then", "what", "when", "where"
    )
    
    fun extractKeywords(text: String, topN: Int = 5): List<String> {
        // Tokenize and clean
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in stopWords }
        
        // Count frequency
        val wordFrequency = mutableMapOf<String, Int>()
        for (word in words) {
            wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
        }
        
        // Get top N
        return wordFrequency.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }
    
    fun extractKeywordsFromMultipleTexts(texts: List<String>, topN: Int = 10): List<String> {
        val allText = texts.joinToString(" ")
        return extractKeywords(allText, topN)
    }
    
    fun generateTitle(text: String): String {
        val keywords = extractKeywords(text, 3)
        if (keywords.isEmpty()) return "Untitled Conversation"
        
        return keywords.joinToString(" ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
    }
}