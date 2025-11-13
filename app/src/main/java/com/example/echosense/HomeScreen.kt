package com.echosense.ui.screens

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO
        )
    )
    
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Title
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
            
            Spacer(modifier = Modifier.height(100.dp))
            
            // Main Mic Button with glow effect
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
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
                
                // Main button
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .clickable {
                            if (permissionsState.allPermissionsGranted) {
                                navController.navigate(Screen.LiveCapture.route)
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
                text = "Smart offline/online speech recognition",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Speaker Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SpeakerIndicator(number = 1, color = Color(0xFF00BCD4), isActive = false)
                SpeakerIndicator(number = 2, color = Color(0xFFFF9800), isActive = false)
                SpeakerIndicator(number = 3, color = Color(0xFF9C27B0), isActive = false)
                SpeakerIndicator(number = 4, color = Color(0xFF4CAF50), isActive = false)
            }
            
            Text(
                text = "Automatic speaker detection",
                fontSize = 11.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 12.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Features list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureItem(
                    icon = Icons.Default.Mic,
                    text = "Real-time Speech Recognition"
                )
                FeatureItem(
                    icon = Icons.Default.CloudOff,
                    text = "Works Offline (when available)"
                )
                FeatureItem(
                    icon = Icons.Default.People,
                    text = "Speaker Diarization"
                )
                FeatureItem(
                    icon = Icons.Default.Note,
                    text = "Auto Note Extraction"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
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
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White
        )
    }
}