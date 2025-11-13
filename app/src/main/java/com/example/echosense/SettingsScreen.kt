package com.echosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.echosense.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    var showClearDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Data & Storage Section
            SectionHeader("Data & Storage")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    SettingsButton(
                        text = "Clear All Sessions",
                        color = Color(0xFFF44336),
                        onClick = { showClearDialog = true }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingsButton(
                        text = "Export All Data",
                        color = Color(0xFF00BCD4),
                        onClick = { /* Export functionality */ }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Recording Quality Section
            SectionHeader("Recording Quality")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    var selectedQuality by remember { mutableStateOf("medium") }
                    
                    RadioButtonOption(
                        text = "Low (8 kHz)",
                        selected = selectedQuality == "low",
                        onSelect = { selectedQuality = "low" }
                    )
                    
                    RadioButtonOption(
                        text = "Medium (16 kHz)",
                        selected = selectedQuality == "medium",
                        onSelect = { selectedQuality = "medium" }
                    )
                    
                    RadioButtonOption(
                        text = "High (44.1 kHz)",
                        selected = selectedQuality == "high",
                        onSelect = { selectedQuality = "high" }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Speaker Labels Section
            SectionHeader("Speaker Labels")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                SettingsListItem(
                    text = "Customize Speaker Names",
                    onClick = { /* Navigate to speaker labels */ }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Backup Options Section
            SectionHeader("Backup Options")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                SettingsListItem(
                    text = "Cloud Backup",
                    onClick = { /* Navigate to backup options */ }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Theme Section
            SectionHeader("App Theme")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    var selectedTheme by remember { mutableStateOf("dark") }
                    
                    RadioButtonOption(
                        text = "Light",
                        selected = selectedTheme == "light",
                        onSelect = { selectedTheme = "light" }
                    )
                    
                    RadioButtonOption(
                        text = "Dark",
                        selected = selectedTheme == "dark",
                        onSelect = { selectedTheme = "dark" }
                    )
                    
                    RadioButtonOption(
                        text = "AMOLED Black",
                        selected = selectedTheme == "amoled",
                        onSelect = { selectedTheme = "amoled" }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // About Section
            SectionHeader("About")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Version 1.0.0",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Real-time speech recognition with offline speaker diarization",
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
    
    // Clear All Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will delete all sessions, transcripts, and notes. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        content()
    }
}

@Composable
fun SettingsButton(text: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = SolidColor(color)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, color = color)
    }
}

@Composable
fun RadioButtonOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF00BCD4),
                unselectedColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

@Composable
fun SettingsListItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = Color.Gray
        )
    }
}