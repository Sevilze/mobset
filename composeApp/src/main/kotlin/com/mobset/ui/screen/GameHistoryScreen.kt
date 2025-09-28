package com.mobset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import com.mobset.ui.viewmodel.ProfileViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(onNavigateBack: () -> Unit) {
    val vm: ProfileViewModel = hiltViewModel()
    val games by vm.games.collectAsState()
    val filters by vm.filters.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Game History") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GameModeFilter(current = filters.gameMode, onSelect = vm::setGameMode)
                PlayerModeFilter(current = filters.playerMode, onSelect = vm::setPlayerMode)
            }
            SortRow()
            Divider()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games) { g ->
                    GameHistoryItem(
                        title = "${g.playerMode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${g.gameMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        subtitle = "Players: ${g.totalPlayers} · Winners: ${g.winners.joinToString()}",
                        time = (g.finishTimestamp - g.creationTimestamp).coerceAtLeast(0L)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow() {
    var ascending by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("Sort by date")
        Spacer(Modifier.width(8.dp))
        AssistChip(onClick = { ascending = !ascending }, label = { Text(if (ascending) "Ascending" else "Descending") })
    }
}

@Composable
private fun GameHistoryItem(title: String, subtitle: String, time: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Duration: ${formatDuration(time)}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
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
            DropdownMenuItem(text = { Text("Normal") }, onClick = { onSelect(GameModeType.NORMAL); expanded = false })
            DropdownMenuItem(text = { Text("Ultra") }, onClick = { onSelect(GameModeType.ULTRA); expanded = false })
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


