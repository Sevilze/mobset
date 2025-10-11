package com.mobset.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation destinations for the app.
 */
sealed class BottomDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Singleplayer : BottomDestination(
        "singleplayer",
        "Singleplayer",
        Icons.Filled.PlayArrow
    )

    data object Multiplayer : BottomDestination("multiplayer", "Multiplayer", Icons.Filled.Home)

    data object Playground : BottomDestination("playground", "Playground", Icons.Filled.Refresh)

    data object Friends : BottomDestination("friends", "Friends", Icons.Filled.Person)

    data object Profile : BottomDestination("profile", "Profile", Icons.Filled.AccountCircle)

    companion object {
        val items = listOf(Singleplayer, Multiplayer, Playground, Friends, Profile)
    }
}

/**
 * Material 3 bottom navigation bar with expressive animations.
 */
@Composable
fun BottomNavBar(currentRoute: String?, onSelect: (BottomDestination) -> Unit) {
    NavigationBar {
        BottomDestination.items.forEach { dest ->
            val selected = currentRoute == dest.route
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.1f else 1f,
                animationSpec = tween(200),
                label = "icon_scale"
            )

            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        modifier =
                        Modifier
                            .scale(scale)
                            .size(24.dp)
                    )
                },
                label = { Text(dest.label) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
