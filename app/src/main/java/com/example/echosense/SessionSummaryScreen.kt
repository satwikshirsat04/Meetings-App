package com.echosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.echosense.models.NoteType
import com.echosense.viewmodels.SessionSummaryViewModel
import com.echosense.viewmodels.SessionSummaryViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    navController: NavController,
    sessionId: Long,
    viewModel: SessionSummaryViewModel = viewModel(
        factory = SessionSummaryViewModelFactory(LocalContext.current, sessionId)
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showTranscript by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadSessionData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation Summary") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Waveform visualization at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = Color(0xFF1A1A1A),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(30) { index ->
                            val height = (20 + (index % 7) * 8).dp
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(height)
                                    .background(
                                        color = Color(0xFF00BCD4).copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Speaker circles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val colors = listOf(
                        Color(0xFF00BCD4),
                        Color(0xFFFF9800),
                        Color(0xFF9C27B0),
                        Color(0xFF4CAF50)
                    )
                    
                    repeat(uiState.session?.speakerCount ?: 0) { index ->
                        SpeakerBadge(
                            number = index + 1,
                            color = colors[index % colors.size]
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Session Info
                uiState.session?.let { session ->
                    Text(
                        text = session.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = formatDate(session.startTime),
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    
                    Text(
                        text = formatDuration(session.duration),
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Notes Section
                Text(
                    text = "Notes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val keyPoints = uiState.notes.filter { it.type == NoteType.KEY_POINT }
                        val actionItems = uiState.notes.filter { it.type == NoteType.ACTION_ITEM }
                        val decisions = uiState.notes.filter { it.type == NoteType.DECISION }
                        
                        if (keyPoints.isNotEmpty()) {
                            NoteSection("Key Points:", keyPoints.map { it.content })
                        }
                        
                        if (actionItems.isNotEmpty()) {
                            NoteSection("Action Items:", actionItems.map { it.content })
                        }
                        
                        if (decisions.isNotEmpty()) {
                            NoteSection("Decisions:", decisions.map { it.content })
                        }
                        
                        if (uiState.notes.isEmpty()) {
                            Text(
                                text = "No notes extracted from this conversation.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Speaker Breakdown
                Text(
                    text = "Speaker Breakdown",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        uiState.speakers.forEachIndexed { index, speaker ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            
                            Text(
                                text = "Speaker ${index + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Speech segments: ${uiState.transcript.count { it.speakerId == index.toLong() }}",
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        if (uiState.speakers.isEmpty()) {
                            Text(
                                text = "No speaker data available",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Transcript Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Full Transcript",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    TextButton(onClick = { showTranscript = !showTranscript }) {
                        Text(
                            text = if (showTranscript) "Hide" else "Show",
                            color = Color(0xFF00BCD4)
                        )
                    }
                }
                
                if (showTranscript) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            uiState.transcript.forEach { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF252525)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = entry.speakerLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00BCD4)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = entry.text,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            
                            if (uiState.transcript.isEmpty()) {
                                Text(
                                    text = "No transcript available",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Bottom Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
                
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete")
                }
                
                Button(
                    onClick = { showShareDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BCD4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Share")
                }
            }
        }
    }
    
    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete this session? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession {
                        navController.navigateUp()
                    }
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
    
    // Share Dialog
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Session") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.shareAsText(context)
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share as Text", modifier = Modifier.fillMaxWidth())
                    }
                    
                    TextButton(
                        onClick = {
                            viewModel.shareAsPDF(context)
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share as PDF", modifier = Modifier.fillMaxWidth())
                    }
                    
                    TextButton(
                        onClick = {
                            viewModel.shareAsJSON(context)
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share as JSON", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun SpeakerBadge(number: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = color
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Speaker $number",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Speaker $number",
            fontSize = 10.sp,
            color = Color.White
        )
    }
}

@Composable
fun NoteSection(title: String, items: List<String>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00BCD4)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF00BCD4), shape = CircleShape)
                        .align(Alignment.Top)
                ) {
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
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