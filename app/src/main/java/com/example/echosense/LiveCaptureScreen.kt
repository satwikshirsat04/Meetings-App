package com.echosense.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.echosense.ui.navigation.Screen
import com.echosense.viewmodels.LiveCaptureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(
    navController: NavController,
    speakerCount: Int,
    viewModel: LiveCaptureViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LiveCaptureViewModel(speakerCount) as T
            }
        }
    )
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

            Spacer(modifier = Modifier.height(8.dp))

            // Recognition Mode & Processing Status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RecognitionModeIndicator(
                    mode = uiState.recognitionMode,
                    isListening = uiState.isListening,
                    errorMessage = uiState.errorMessage
                )

                Spacer(modifier = Modifier.height(8.dp))

                ProcessingStatusBar(
                    status = uiState.processingStatus,
                    isActive = uiState.isListening
                )
            }

            // Dynamic Waveform with actual amplitude
            DynamicWaveformVisualization(
                amplitude = uiState.currentAmplitude,
                isActive = uiState.isListening
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Speaker Circles with circular rotating glow
            // Use scrollable row for 5-6 speakers, regular for 2-4
            if (speakerCount <= 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val colors = listOf(
                        Color(0xFF00BCD4),
                        Color(0xFFFF9800),
                        Color(0xFF9C27B0),
                        Color(0xFF4CAF50),
                        Color(0xFFE91E63),
                        Color(0xFFFFEB3B)
                    )

                    repeat(speakerCount) { index ->
                        SpeakerCircleWithRotatingGlow(
                            number = index + 1,
                            color = colors[index % colors.size],
                            isActive = uiState.activeSpeaker == index,
                            speakingLevel = if (uiState.activeSpeaker == index) uiState.currentAmplitude else 0f
                        )
                    }
                }
            } else {
                // Scrollable row for 5-6 speakers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val colors = listOf(
                        Color(0xFF00BCD4),
                        Color(0xFFFF9800),
                        Color(0xFF9C27B0),
                        Color(0xFF4CAF50),
                        Color(0xFFE91E63),
                        Color(0xFFFFEB3B)
                    )

                    repeat(speakerCount) { index ->
                        SpeakerCircleWithRotatingGlow(
                            number = index + 1,
                            color = colors[index % colors.size],
                            isActive = uiState.activeSpeaker == index,
                            speakingLevel = if (uiState.activeSpeaker == index) uiState.currentAmplitude else 0f
                        )
                    }
                }
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

                        Text(
                            text = "${uiState.transcriptEntries.size} entries",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            uiState.transcriptEntries.takeLast(20).forEach { entry ->
                                EnhancedTranscriptItem(
                                    speakerLabel = entry.speakerLabel,
                                    text = entry.text,
                                    confidence = entry.confidence,
                                    isRecent = uiState.transcriptEntries.lastOrNull() == entry
                                )
                            }

                            if (uiState.transcriptEntries.isEmpty()) {
                                EmptyTranscriptPlaceholder()
                            }

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
                title = { Text("End Session", color = Color.White) },
                text = {
                    Column {
                        Text(
                            "Are you sure you want to end this recording session?",
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.transcriptEntries.size} transcript entries recorded",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.endRecording(context) { sessionId ->
                            navController.navigate(Screen.SessionSummary.createRoute(sessionId)) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }) {
                        Text("End & Process Notes", color = Color(0xFFF44336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEndDialog() }) {
                        Text("Cancel", color = Color(0xFF00BCD4))
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }
    }
}

// ======================== DYNAMIC WAVEFORM WITH ACTUAL AMPLITUDE ========================
@Composable
fun DynamicWaveformVisualization(amplitude: Float, isActive: Boolean) {
    // Store bar heights to create smooth transitions
    val barHeights = remember { mutableStateListOf<Float>().apply { repeat(30) { add(0.3f) } } }

    // Update bar heights based on actual amplitude
    LaunchedEffect(amplitude, isActive) {
        while (isActive) {
            // Shift heights to create wave effect
            for (i in barHeights.indices.reversed()) {
                if (i == 0) {
                    // New bar height based on actual amplitude with some randomness
                    val newHeight = if (amplitude > 0.01f) {
                        amplitude.coerceIn(0.2f, 1f) + (Random.nextFloat() * 0.2f - 0.1f)
                    } else {
                        0.2f + (Random.nextFloat() * 0.1f)
                    }
                    barHeights[i] = newHeight.coerceIn(0.2f, 1f)
                } else {
                    // Smooth transition from previous bar
                    barHeights[i] = barHeights[i - 1] * 0.95f
                }
            }
            delay(50) // Update every 50ms for smooth animation
        }
    }

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
            barHeights.forEachIndexed { index, heightFactor ->
                val baseHeight = 20.dp
                val maxHeight = 70.dp
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

// ======================== SPEAKER WITH CIRCULAR ROTATING GLOW ========================
@Composable
fun SpeakerCircleWithRotatingGlow(
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

    // Rotation animation for the glow ring
    val infiniteTransition = rememberInfiniteTransition(label = "glowRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing glow intensity
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(90.dp)
    ) {
        // Rotating circular glow effect
        if (isActive) {
            Canvas(
                modifier = Modifier
                    .size(85.dp)
                    .rotate(rotation)
            ) {
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)

                // Draw multiple circular rings for depth
                for (i in 0..2) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = glowIntensity * (0.6f - i * 0.2f)),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius + (i * 10f)
                        ),
                        radius = radius + (i * 10f),
                        center = center
                    )
                }

                // Animated arc segments for rotation effect
                for (angle in 0..360 step 60) {
                    val path = Path().apply {
                        addArc(
                            oval = androidx.compose.ui.geometry.Rect(
                                center.x - radius,
                                center.y - radius,
                                center.x + radius,
                                center.y + radius
                            ),
                            startAngleDegrees = angle.toFloat(),
                            sweepAngleDegrees = 30f
                        )
                    }
                    drawPath(
                        path = path,
                        color = color.copy(alpha = glowIntensity * 0.5f),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }

        // Speaker circle
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

// ======================== REST OF THE COMPOSABLES ========================

@Composable
fun ProcessingStatusBar(status: String, isActive: Boolean) {
    val backgroundColor = if (isActive) Color(0xFF2196F3).copy(alpha = 0.2f) else Color(0xFF424242)
    val textColor = if (isActive) Color(0xFF2196F3) else Color.Gray

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = status,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (errorMessage.isEmpty()) {
                    if (isListening) Icons.Default.Mic else Icons.Default.MicOff
                } else Icons.Default.Warning,
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
fun EnhancedTranscriptItem(
    speakerLabel: String,
    text: String,
    confidence: Float,
    isRecent: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = if (isRecent) 1f else 0.85f,
        animationSpec = tween(300),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263238)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speakerLabel.split(" ").lastOrNull() ?: speakerLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = Color(0xFFEEEEEE),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Confidence: ${(confidence * 100).toInt()}%",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    if (isRecent) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "New",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color(0xFF2E7D32),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PartialTranscriptItem(speakerLabel: String, text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "$speakerLabel (partial)",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun EmptyTranscriptPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.TextSnippet,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No transcript yet — speaking will appear here in real time.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun AnimatedControlButton(
    onClick: () -> Unit,
    containerColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    val interactionScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Surface(
        onClick = {
            scope.launch {
                interactionScale.animateTo(0.95f, animationSpec = tween(80))
                interactionScale.animateTo(1f, animationSpec = tween(120))
            }
            onClick()
        },
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .scale(interactionScale.value),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatElapsedTime(elapsedMillis: Long): String {
    val secondsTotal = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis)
    val hours = secondsTotal / 3600
    val minutes = (secondsTotal % 3600) / 60
    val seconds = secondsTotal % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
