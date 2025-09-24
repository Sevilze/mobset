package com.mobset

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobset.domain.model.GameMode
import com.mobset.theme.AppTheme
import com.mobset.ui.screen.GameScreen
import com.mobset.ui.screen.HomeScreen

@Composable
fun SetApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToGame = { gameMode ->
                    navController.navigate("game/${gameMode.id}")
                }
            )
        }

        composable(
            route = "game/{gameModeId}",
            arguments = listOf(navArgument("gameModeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val gameModeId = backStackEntry.arguments?.getString("gameModeId") ?: "normal"
            val gameMode = GameMode.fromId(gameModeId) ?: GameMode.NORMAL

            GameScreen(
                gameMode = gameMode,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Preview
@Composable
fun PreviewSetApp() {
    AppTheme { SetApp() }
}

