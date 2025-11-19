package com.echosense.ui.screens

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.echosense.ui.navigation.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.RECORD_AUDIO)
    )

    var showSpeakerDialog by remember { mutableStateOf(false) }
    var selectedSpeakerCount by remember { mutableStateOf(4) }

    // pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "EchoSense",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "AI-Powered Conversation Analysis",
                fontSize = 14.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Speaker selector card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSpeakerDialog = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Speakers",
                            tint = Color(0xFF00BCD4),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Number of Speakers: $selectedSpeakerCount",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF00BCD4),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Mic Button + Glow
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00BCD4).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .clickable {
                            if (permissionsState.allPermissionsGranted) {
                                navController.navigate(
                                    Screen.LiveCapture.createRoute(selectedSpeakerCount)
                                )
                            } else {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00BCD4).copy(alpha = 0.4f),
                                        Color(0xFF00BCD4).copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = Color(0xFF00BCD4),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tap to Start Recording",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Smart offline speech recognition",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Speaker Preview
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                repeat(selectedSpeakerCount) { index ->
                    val colors = listOf(
                        Color(0xFF00BCD4),
                        Color(0xFFFF9800),
                        Color(0xFF9C27B0),
                        Color(0xFF4CAF50),
                        Color(0xFFE91E63),
                        Color(0xFFFFEB3B)
                    )
                    SpeakerIndicator(
                        number = index + 1,
                        color = colors[index % colors.size],
                        isActive = false
                    )
                }
            }

            Text(
                text = "Automatic speaker detection",
                fontSize = 11.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature list (commented out as in original)
            // Column(...) { ... }

            // FIXED: Added weight to push bottom navigation up and ensure proper spacing
            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation - FIXED POSITIONING
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1A),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp), // Reduced vertical padding
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BottomNavButton(
                        icon = Icons.Default.Description,
                        label = "Notes",
                        onClick = { navController.navigate(Screen.Notes.route) }
                    )
                    BottomNavButton(
                        icon = Icons.Default.History,
                        label = "History",
                        onClick = { navController.navigate(Screen.History.route) }
                    )
                    BottomNavButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
        }
    }

    // Speaker Dialog (unchanged)
    if (showSpeakerDialog) {
        AlertDialog(
            onDismissRequest = { showSpeakerDialog = false },
            title = {
                Text("Select Number of Speakers", color = Color.White)
            },
            text = {
                Column {
                    (2..6).forEach { count ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSpeakerCount = count
                                    showSpeakerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSpeakerCount == count,
                                onClick = {
                                    selectedSpeakerCount = count
                                    showSpeakerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF00BCD4),
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$count Speakers",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeakerDialog = false }) {
                    Text("Cancel", color = Color(0xFF00BCD4))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun SpeakerIndicator(number: Int, color: Color, isActive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Speaker $number",
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$number",
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00BCD4),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

@Composable
fun BottomNavButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF00BCD4).copy(alpha = 0.2f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFF00BCD4),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}