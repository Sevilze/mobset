package com.mobset

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.domain.model.GameMode
import com.mobset.theme.AppTheme
import com.mobset.ui.screen.*
import com.mobset.ui.viewmodel.AuthViewModel
import com.mobset.ui.viewmodel.ProfileViewModel


private enum class RootRoute(val route: String) { Loading("loading"), Root("root") }

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    data object Singleplayer : BottomTab("singleplayer", "Singleplayer", Icons.Outlined.PlayArrow)
    data object Multiplayer : BottomTab("multiplayer", "Multiplayer", Icons.Outlined.Group)
    data object Playground : BottomTab("playground", "Playground", Icons.Outlined.Toys)
    data object Friends : BottomTab("friends", "Friends", Icons.Outlined.Home)
    data object Profile : BottomTab("profile", "Profile", Icons.Outlined.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SetApp() {
    val navController = rememberNavController()
    var isLoading by remember { mutableStateOf(true) }

    val authViewModel: AuthViewModel = hiltViewModel()
    val user by authViewModel.currentUser.collectAsState()

    if (isLoading) {
        LoadingScreen(onLoadingComplete = { isLoading = false })
        return
    }

    if (user == null) {
        SignInScreen(onSignedIn = { /* Nav will auto-refresh via user state */ })
        return
    }

    val tabs = remember {
        listOf(
            BottomTab.Singleplayer,
            BottomTab.Multiplayer,
            BottomTab.Playground,
            BottomTab.Friends,
            BottomTab.Profile
        )
    }

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val isGameRoute = currentRoute?.startsWith("game") == true
            if (isGameRoute) {
                // Hide standard bottom bar without instantiating game VM here to avoid jank
                Spacer(modifier = Modifier)
            } else {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding: PaddingValues ->
        val routeState by navController.currentBackStackEntryAsState()
        AnimatedContent(
            targetState = routeState?.destination?.route ?: "",
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(
                    animationSpec = tween(
                        200
                    )
                )
            },
            label = "content"
        ) { animatedRoute ->
            key(animatedRoute) {
                NavHost(
                    navController = navController,
                    startDestination = BottomTab.Singleplayer.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable("history") {
                        val profileVm: ProfileViewModel = hiltViewModel()
                        GameHistoryScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenDetail = { record ->
                                profileVm.openHistoryDetail(record)
                                navController.navigate("historyDetail")
                            }
                        )
                    }
                    composable("historyDetail") {
                        val parentEntry =
                            remember(navController) { navController.getBackStackEntry("history") }
                        val sharedVm: ProfileViewModel = hiltViewModel(parentEntry)
                        GameHistoryDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            vm = sharedVm
                        )
                    }
                    composable(BottomTab.Singleplayer.route) {
                        SingleplayerScreen(
                            onNavigateToGame = { gameMode ->
                                navController.navigate("game/${gameMode.id}")
                            }
                        )
                    }
                    composable(BottomTab.Multiplayer.route) {
                        MultiplayerScreen()
                    }
                    composable(BottomTab.Playground.route) {
                        PlaygroundScreen()
                    }
                    composable(BottomTab.Friends.route) {
                        FriendsScreen()
                    }
                    composable(BottomTab.Profile.route) {
                        ProfileScreen(
                            onViewHistory = { navController.navigate("history") },
                            onSignOut = { authViewModel.signOut() }
                        )
                    }

                    composable(
                        route = "game/{gameModeId}",
                        arguments = listOf(navArgument("gameModeId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val gameModeId =
                            backStackEntry.arguments?.getString("gameModeId") ?: "normal"
                        val gameMode = GameMode.fromId(gameModeId) ?: GameMode.NORMAL
                        GameScreen(
                            gameMode = gameMode,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun PreviewSetApp() {
    AppTheme { SetApp() }
}

