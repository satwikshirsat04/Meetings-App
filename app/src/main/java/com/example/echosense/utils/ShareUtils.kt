package com.echosense.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.echosense.models.*
import com.google.gson.Gson
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ShareUtils {
    
    companion object {
        
        fun shareAsText(
            context: Context,
            session: Session,
            transcript: List<TranscriptEntry>,
            notes: List<Note>
        ) {
            val text = buildString {
                appendLine("EchoSense - Conversation Summary")
                appendLine("=" .repeat(50))
                appendLine()
                appendLine("Title: ${session.title}")
                appendLine("Date: ${formatDate(session.startTime)}")
                appendLine("Duration: ${formatDuration(session.duration)}")
                appendLine("Speakers: ${session.speakerCount}")
                appendLine()
                appendLine("=" .repeat(50))
                appendLine()
                
                if (notes.isNotEmpty()) {
                    appendLine("NOTES")
                    appendLine("-" .repeat(50))
                    
                    val keyPoints = notes.filter { it.type == NoteType.KEY_POINT }
                    if (keyPoints.isNotEmpty()) {
                        appendLine("\nKey Points:")
                        keyPoints.forEach { appendLine("• ${it.content}") }
                    }
                    
                    val actionItems = notes.filter { it.type == NoteType.ACTION_ITEM }
                    if (actionItems.isNotEmpty()) {
                        appendLine("\nAction Items:")
                        actionItems.forEach { appendLine("• ${it.content}") }
                    }
                    
                    val decisions = notes.filter { it.type == NoteType.DECISION }
                    if (decisions.isNotEmpty()) {
                        appendLine("\nDecisions:")
                        decisions.forEach { appendLine("• ${it.content}") }
                    }
                    
                    appendLine()
                    appendLine("=" .repeat(50))
                    appendLine()
                }
                
                appendLine("TRANSCRIPT")
                appendLine("-" .repeat(50))
                transcript.forEach { entry ->
                    appendLine("${entry.speakerLabel}: ${entry.text}")
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, session.title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
        
        fun shareAsPDF(
            context: Context,
            session: Session,
            transcript: List<TranscriptEntry>,
            notes: List<Note>
        ) {
            try {
                val fileName = "EchoSense_${session.id}_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val document = Document()
                PdfWriter.getInstance(document, FileOutputStream(file))
                document.open()
                
                // Title
                val titleFont = Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD)
                val title = Paragraph("EchoSense - Conversation Summary", titleFont)
                title.alignment = Element.ALIGN_CENTER
                document.add(title)
                document.add(Paragraph(" "))
                
                // Session info
                val normalFont = Font(Font.FontFamily.HELVETICA, 12f)
                document.add(Paragraph("Title: ${session.title}", normalFont))
                document.add(Paragraph("Date: ${formatDate(session.startTime)}", normalFont))
                document.add(Paragraph("Duration: ${formatDuration(session.duration)}", normalFont))
                document.add(Paragraph("Speakers: ${session.speakerCount}", normalFont))
                document.add(Paragraph(" "))
                
                // Notes
                if (notes.isNotEmpty()) {
                    val headerFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)
                    document.add(Paragraph("NOTES", headerFont))
                    document.add(Paragraph(" "))
                    
                    val subHeaderFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD)
                    
                    val keyPoints = notes.filter { it.type == NoteType.KEY_POINT }
                    if (keyPoints.isNotEmpty()) {
                        document.add(Paragraph("Key Points:", subHeaderFont))
                        keyPoints.forEach {
                            document.add(Paragraph("• ${it.content}", normalFont))
                        }
                        document.add(Paragraph(" "))
                    }
                }
                
                // Transcript
                val headerFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)
                document.add(Paragraph("TRANSCRIPT", headerFont))
                document.add(Paragraph(" "))
                
                transcript.forEach { entry ->
                    val speakerFont = Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD)
                    val textFont = Font(Font.FontFamily.HELVETICA, 11f)
                    
                    val phrase = Phrase()
                    phrase.add(Chunk("${entry.speakerLabel}: ", speakerFont))
                    phrase.add(Chunk(entry.text, textFont))
                    document.add(Paragraph(phrase))
                }
                
                document.close()
                
                // Share the PDF
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun shareAsJSON(
            context: Context,
            session: Session,
            speakers: List<Speaker>,
            transcript: List<TranscriptEntry>,
            notes: List<Note>
        ) {
            val data = mapOf(
                "session" to session,
                "speakers" to speakers,
                "transcript" to transcript,
                "notes" to notes
            )
            
            val gson = Gson()
            val json = gson.toJson(data)
            
            val fileName = "EchoSense_${session.id}_${System.currentTimeMillis()}.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(json)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share JSON via"))
        }
        
        fun shareAllNotes(context: Context, allSessions: List<SessionData>) {
            val text = buildString {
                appendLine("EchoSense - All Notes Export")
                appendLine("=" .repeat(50))
                appendLine("Exported: ${formatDate(System.currentTimeMillis())}")
                appendLine("Total Sessions: ${allSessions.size}")
                appendLine()
                
                allSessions.forEach { sessionData ->
                    appendLine()
                    appendLine("=" .repeat(50))
                    appendLine("SESSION: ${sessionData.session.title}")
                    appendLine("Date: ${formatDate(sessionData.session.startTime)}")
                    appendLine("Duration: ${formatDuration(sessionData.session.duration)}")
                    appendLine("-" .repeat(50))
                    
                    if (sessionData.notes.isNotEmpty()) {
                        val keyPoints = sessionData.notes.filter { it.type == NoteType.KEY_POINT }
                        if (keyPoints.isNotEmpty()) {
                            appendLine("\nKey Points:")
                            keyPoints.forEach { appendLine("• ${it.content}") }
                        }
                    }
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "EchoSense - All Notes")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share All Notes via"))
        }
        
        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
        
        private fun formatDuration(millis: Long): String {
            val seconds = millis / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            
            return when {
                hours > 0 -> String.format("%dh %dm", hours, minutes % 60)
                minutes > 0 -> String.format("%dm %ds", minutes, seconds % 60)
                else -> String.format("%ds", seconds)
            }
        }
    }
    
    data class SessionData(
        val session: Session,
        val speakers: List<Speaker>,
        val transcript: List<TranscriptEntry>,
        val notes: List<Note>
    )
}