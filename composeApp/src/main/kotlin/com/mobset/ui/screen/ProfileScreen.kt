package com.mobset.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter

import com.mobset.ui.viewmodel.ProfileViewModel
import com.mobset.ui.util.formatElapsedTimeMs

import com.mobset.data.history.AggregatedPlayerStats
import com.mobset.data.history.PlayerMode
import com.mobset.data.history.GameModeType


/**
 * Profile screen: user info, stats, and settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onViewHistory: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var animations by remember { mutableStateOf(true) }

    var syncing by remember { mutableStateOf(false) }

    val vm: ProfileViewModel = hiltViewModel()
    val user by vm.currentUser.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val filters by vm.filters.collectAsState()
    val stats = vm.aggregatedStats.collectAsState().value
    val winLoss by vm.winLoss.collectAsState(initial = ProfileViewModel.WinLoss(0,0))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with avatar and centered identity
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            var showEdit by remember { mutableStateOf(false) }
            var editName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = rememberAsyncImagePainter(user?.photoUrl),
                    contentDescription = "Profile image",
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile?.displayName ?: user?.displayName ?: "-", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit name") }
                }
                Text(user?.email ?: "-", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showEdit) {
                AlertDialog(
                    onDismissRequest = { showEdit = false },
                    title = { Text("Edit display name") },
                    text = {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (editName.isNotBlank()) vm.updateDisplayName(editName)
                            showEdit = false
                        }, enabled = editName.isNotBlank() && editName != (profile?.displayName ?: "")) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEdit = false }) { Text("Cancel") }
                    }
                )
            }
        }

        // Account dialog replaces persistent editor



        // Filters card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Filters", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GameModeFilter(current = filters.gameMode, onSelect = vm::setGameMode)
                    PlayerModeFilter(current = filters.playerMode, onSelect = vm::setPlayerMode)
                }
            }
        }

        // Statistics card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onViewHistory) { Text("View detailed history") }
                }
                PieChart(win = winLoss.wins, loss = winLoss.losses)
                StatsGrid(stats)
                if (syncing) {
                    Spacer(Modifier.height(8.dp))
                    ContainedLoadingIndicator(Modifier.size(56.dp))
                    Text("Syncing profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dynamic color")
                    Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptics")
                    Switch(checked = haptics, onCheckedChange = { haptics = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Animations")
                    Switch(checked = animations, onCheckedChange = { animations = it })
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameModeFilter(current: GameModeType?, onSelect: (GameModeType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = current?.name ?: "All Modes"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            readOnly = true,
            value = label,
            onValueChange = {},
            label = { Text("Game Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().width(160.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            DropdownMenuItem(text = { Text("Normal") }, onClick = { onSelect(com.mobset.data.history.GameModeType.NORMAL); expanded = false })
            DropdownMenuItem(text = { Text("Ultra") }, onClick = { onSelect(com.mobset.data.history.GameModeType.ULTRA); expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerModeFilter(current: PlayerMode?, onSelect: (PlayerMode?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = current?.name ?: "All Players"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            readOnly = true,
            value = label,
            onValueChange = {},
            label = { Text("Player Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().width(180.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            DropdownMenuItem(text = { Text("Solo") }, onClick = { onSelect(PlayerMode.SOLO); expanded = false })
            DropdownMenuItem(text = { Text("Multiplayer") }, onClick = { onSelect(PlayerMode.MULTIPLAYER); expanded = false })
        }
    }
}

@Composable
private fun PieChart(win: Int, loss: Int) {
    val total = (win + loss).coerceAtLeast(1)
    val winAngle = 360f * (win.toFloat() / total.toFloat())
    val lossAngle = 360f - winAngle
    val winColor = MaterialTheme.colorScheme.primary
    val lossColor = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val d = size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val radius = d / 2f
        val rect = Rect(
            left = cx - radius,
            top = cy - radius,
            right = cx + radius,
            bottom = cy + radius
        )
        // Wins slice
        val p1 = Path().apply {
            moveTo(cx, cy)
            arcTo(rect, -90f, winAngle, false)
            close()
        }
        val canvas = this.drawContext.canvas
        val paint1 = Paint().apply { color = winColor }
        val paint2 = Paint().apply { color = lossColor }
        canvas.drawPath(p1, paint1)
        // Loss slice
        val p2 = Path().apply {
            moveTo(cx, cy)
            arcTo(rect, -90f + winAngle, lossAngle, false)
            close()
        }
        canvas.drawPath(p2, paint2)
    }
}

@Composable
private fun StatsGrid(stats: AggregatedPlayerStats?) {
    val s = stats
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Finished games", s?.finishedGames?.toString() ?: "-", Modifier.weight(1f))
            StatCard("Total sets", s?.totalSetsFound?.toString() ?: "-", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Avg sets/game", s?.averageSetsPerGame?.let { String.format("%.2f", it) } ?: "-", Modifier.weight(1f))
            StatCard("Fastest win", s?.fastestGameWonMs?.let { formatElapsedTimeMs(it) } ?: "-", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Avg length", s?.averageGameLengthMs?.let { formatElapsedTimeMs(it) } ?: "-", Modifier.weight(1f))
            StatCard("Rating", s?.rating?.toString() ?: "N/A", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
@Preview
@Composable
private fun StatsGridPreview() {
    val stats = com.mobset.data.history.AggregatedPlayerStats(
        playerId = "demo",
        finishedGames = 12,
        totalSetsFound = 57,
        averageSetsPerGame = 4.75,
        fastestGameWonMs = 95_000,
        averageGameLengthMs = 180_000,
        rating = 1420
    )
    StatsGrid(stats)
}


@Preview
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen()
}


