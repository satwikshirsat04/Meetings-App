package com.echosense.ml

import com.echosense.models.Note
import com.echosense.models.NoteType
import com.echosense.models.TranscriptEntry

class NoteExtractor {
    
    private val actionVerbs = setOf(
        "will", "should", "must", "need", "have to", "going to", "plan to",
        "schedule", "arrange", "organize", "prepare", "contact", "call",
        "email", "send", "review", "check", "update", "create", "finish"
    )
    
    private val decisionWords = setOf(
        "decided", "agreed", "confirmed", "approved", "rejected",
        "chosen", "selected", "final", "conclusion"
    )
    
    private val questionWords = setOf(
        "what", "when", "where", "who", "why", "how", "which",
        "?", "clarify", "explain", "wondering"
    )
    
    private val importantPhrases = setOf(
        "important", "critical", "key point", "remember", "note that",
        "keep in mind", "don't forget", "main", "primary", "essential"
    )
    
    fun extractNotes(transcriptEntries: List<TranscriptEntry>, sessionId: Long): List<Note> {
        val notes = mutableListOf<Note>()
        
        for (entry in transcriptEntries) {
            val text = entry.text.lowercase()
            
            // Detect action items
            if (containsActionVerb(text)) {
                notes.add(Note(
                    sessionId = sessionId,
                    type = NoteType.ACTION_ITEM,
                    content = entry.text,
                    timestamp = entry.timestamp,
                    speakerId = entry.speakerId
                ))
            }
            
            // Detect decisions
            if (containsDecisionWord(text)) {
                notes.add(Note(
                    sessionId = sessionId,
                    type = NoteType.DECISION,
                    content = entry.text,
                    timestamp = entry.timestamp,
                    speakerId = entry.speakerId
                ))
            }
            
            // Detect questions
            if (containsQuestionWord(text) || text.contains("?")) {
                notes.add(Note(
                    sessionId = sessionId,
                    type = NoteType.QUESTION,
                    content = entry.text,
                    timestamp = entry.timestamp,
                    speakerId = entry.speakerId
                ))
            }
            
            // Detect key points
            if (containsImportantPhrase(text) || isLongStatement(entry.text)) {
                notes.add(Note(
                    sessionId = sessionId,
                    type = NoteType.KEY_POINT,
                    content = entry.text,
                    timestamp = entry.timestamp,
                    speakerId = entry.speakerId
                ))
            }
        }
        
        return notes
    }
    
    private fun containsActionVerb(text: String): Boolean {
        return actionVerbs.any { text.contains(it) }
    }
    
    private fun containsDecisionWord(text: String): Boolean {
        return decisionWords.any { text.contains(it) }
    }
    
    private fun containsQuestionWord(text: String): Boolean {
        return questionWords.any { text.contains(it) }
    }
    
    private fun containsImportantPhrase(text: String): Boolean {
        return importantPhrases.any { text.contains(it) }
    }
    
    private fun isLongStatement(text: String): Boolean {
        return text.split(" ").size > 15
    }
}