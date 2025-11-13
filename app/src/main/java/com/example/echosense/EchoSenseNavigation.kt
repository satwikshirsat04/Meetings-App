package com.echosense.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echosense.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object LiveCapture : Screen("live_capture")
    object SessionSummary : Screen("session_summary/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_summary/$sessionId"
    }
    object Notes : Screen("notes")
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun EchoSenseNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        
        composable(Screen.LiveCapture.route) {
            LiveCaptureScreen(navController)
        }
        
        composable(
            route = Screen.SessionSummary.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            SessionSummaryScreen(navController, sessionId)
        }
        
        composable(Screen.Notes.route) {
            NotesScreen(navController)
        }
        
        composable(Screen.History.route) {
            HistoryScreen(navController)
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }
    }
}