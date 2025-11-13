package com.echosense.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.echosense.ui.navigation.Screen
import com.echosense.viewmodels.LiveCaptureViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(
    navController: NavController,
    viewModel: LiveCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var elapsedTime by remember { mutableStateOf(0L) }
    var isRunning by remember { mutableStateOf(true) }
    
    LaunchedEffect(isRunning) {
        val startTime = SystemClock.elapsedRealtime()
        while (isRunning) {
            delay(1000)
            elapsedTime = SystemClock.elapsedRealtime() - startTime
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.startRecording(context)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            isRunning = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Live Conversation",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        RecordingIndicator()
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = formatElapsedTime(elapsedTime),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.showEndDialog()
                    }) {
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recognition Mode Status
            RecognitionModeIndicator(
                mode = uiState.recognitionMode,
                isListening = uiState.isListening,
                errorMessage = uiState.errorMessage
            )
            
            // Enhanced Waveform
            EnhancedWaveformVisualization(
                amplitude = uiState.currentAmplitude,
                isActive = uiState.isListening
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Enhanced Speaker Circles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedSpeakerCircle(
                    number = 1,
                    color = Color(0xFF00BCD4),
                    isActive = uiState.activeSpeaker == 0,
                    speakingLevel = if (uiState.activeSpeaker == 0) uiState.currentAmplitude else 0f
                )
                EnhancedSpeakerCircle(
                    number = 2,
                    color = Color(0xFFFF9800),
                    isActive = uiState.activeSpeaker == 1,
                    speakingLevel = if (uiState.activeSpeaker == 1) uiState.currentAmplitude else 0f
                )
                EnhancedSpeakerCircle(
                    number = 3,
                    color = Color(0xFF9C27B0),
                    isActive = uiState.activeSpeaker == 2,
                    speakingLevel = if (uiState.activeSpeaker == 2) uiState.currentAmplitude else 0f
                )
                EnhancedSpeakerCircle(
                    number = 4,
                    color = Color(0xFF4CAF50),
                    isActive = uiState.activeSpeaker == 3,
                    speakingLevel = if (uiState.activeSpeaker == 3) uiState.currentAmplitude else 0f
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Enhanced Transcript Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Transcript",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        if (uiState.isListening) {
                            LiveIndicator()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            uiState.transcriptEntries.takeLast(10).forEach { entry ->
                                EnhancedTranscriptItem(
                                    speakerLabel = entry.speakerLabel,
                                    text = entry.text,
                                    isRecent = uiState.transcriptEntries.lastOrNull() == entry
                                )
                            }
                            
                            if (uiState.transcriptEntries.isEmpty()) {
                                EmptyTranscriptPlaceholder()
                            }
                            
                            // Show current partial text
                            if (uiState.currentPartialText.isNotEmpty()) {
                                PartialTranscriptItem(
                                    speakerLabel = "Speaker ${(uiState.activeSpeaker ?: 0) + 1}",
                                    text = uiState.currentPartialText
                                )
                            }
                        }
                    }
                }
            }
            
            // Enhanced Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                AnimatedControlButton(
                    onClick = { viewModel.togglePause() },
                    containerColor = if (uiState.isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    icon = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    text = if (uiState.isPaused) "Resume" else "Pause"
                )
                
                AnimatedControlButton(
                    onClick = { viewModel.showEndDialog() },
                    containerColor = Color(0xFFF44336),
                    icon = Icons.Default.Stop,
                    text = "End"
                )
                
                IconButton(
                    onClick = { viewModel.addBookmark() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Add Marker",
                        tint = Color.White
                    )
                }
            }
        }
        
        // End Dialog
        if (uiState.showEndDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEndDialog() },
                title = { Text("End Session") },
                text = { Text("Are you sure you want to end this recording session?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.endRecording(context) { sessionId ->
                            navController.navigate(Screen.SessionSummary.createRoute(sessionId)) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }) {
                        Text("End", color = Color(0xFFF44336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEndDialog() }) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }
    }
}

@Composable
fun RecognitionModeIndicator(
    mode: String,
    isListening: Boolean,
    errorMessage: String
) {
    val backgroundColor = when {
        errorMessage.isNotEmpty() -> Color(0xFFF44336).copy(alpha = 0.2f)
        mode.contains("Offline", ignoreCase = true) -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        mode.contains("Online", ignoreCase = true) -> Color(0xFF2196F3).copy(alpha = 0.2f)
        else -> Color(0xFFFF9800).copy(alpha = 0.2f)
    }
    
    val iconColor = when {
        errorMessage.isNotEmpty() -> Color(0xFFF44336)
        mode.contains("Offline", ignoreCase = true) -> Color(0xFF4CAF50)
        mode.contains("Online", ignoreCase = true) -> Color(0xFF2196F3)
        else -> Color(0xFFFF9800)
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (errorMessage.isEmpty()) Icons.Default.Mic else Icons.Default.Warning,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (errorMessage.isEmpty()) mode else "Error",
                    color = iconColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = iconColor.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = alpha))
    )
}

@Composable
fun EnhancedWaveformVisualization(amplitude: Float, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(30) { index ->
                val animatedHeight by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 400 + (index * 20),
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$index"
                )
                
                val baseHeight = 20.dp
                val maxHeight = 70.dp
                val heightFactor = if (isActive) amplitude.coerceIn(0.3f, 1f) * animatedHeight else 0.3f
                val height = baseHeight + ((maxHeight - baseHeight) * heightFactor)
                
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF00BCD4),
                                    Color(0xFF00BCD4).copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun EnhancedSpeakerCircle(
    number: Int,
    color: Color,
    isActive: Boolean,
    speakingLevel: Float
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val glowAlpha = if (isActive) speakingLevel.coerceIn(0.3f, 0.8f) else 0f
    
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(1.2f)
                    .blur(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = glowAlpha))
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .size(65.dp)
                    .scale(scale),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) color else color.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isActive) 12.dp else 4.dp
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Speaker $number",
                fontSize = 12.sp,
                color = if (isActive) Color.White else Color.Gray,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFF44336).copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF44336).copy(alpha = alpha)
        )
    }
}

@Composable
fun EnhancedTranscriptItem(speakerLabel: String, text: String, isRecent: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (isRecent) 1f else 0.7f,
        animationSpec = tween(300),
        label = "alpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF252525).copy(alpha = alpha)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = speakerLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun PartialTranscriptItem(speakerLabel: String, text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "partial")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF252525).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = speakerLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BCD4).copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "typing...",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun EmptyTranscriptPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Start speaking...",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your conversation will appear here in real-time",
            color = Color.Gray.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun AnimatedControlButton(
    onClick: () -> Unit,
    containerColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor
        ),
        modifier = Modifier
            .height(50.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

fun formatElapsedTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000 / 60) % 60
    val hours = millis / 1000 / 60 / 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}