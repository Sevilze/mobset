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
import com.mobset.data.history.GameRecord
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import com.mobset.ui.viewmodel.ProfileViewModel
import com.mobset.ui.util.formatElapsedTimeMs
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(onNavigateBack: () -> Unit, onOpenDetail: (GameRecord) -> Unit) {
    val vm: ProfileViewModel = hiltViewModel()
    val games by vm.games.collectAsState()
    val filters by vm.filters.collectAsState()
    val winnerNames by vm.winnerNames.collectAsState()

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
            // Sorting controls
            var sortKey by remember { mutableStateOf("Date") }
            var ascending by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { ascending = !ascending }) { Text(if (ascending) "Ascending" else "Descending") }
                Spacer(Modifier.width(8.dp))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        readOnly = true,
                        value = sortKey,
                        onValueChange = {},
                        label = { Text("Sort by") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().width(180.dp)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Date") }, onClick = { sortKey = "Date"; expanded = false })
                        DropdownMenuItem(text = { Text("Sets found") }, onClick = { sortKey = "Sets found"; expanded = false })
                        DropdownMenuItem(text = { Text("Elapsed time") }, onClick = { sortKey = "Elapsed time"; expanded = false })
                    }
                }
            }

            val sortedGames = remember(games, sortKey, ascending) {
                when (sortKey) {
                    "Sets found" -> if (ascending) games.sortedBy { it.playerStats.sumOf { s -> s.setsFound } } else games.sortedByDescending { it.playerStats.sumOf { s -> s.setsFound } }
                    "Elapsed time" -> if (ascending) games.sortedBy { it.finishTimestamp - it.creationTimestamp } else games.sortedByDescending { it.finishTimestamp - it.creationTimestamp }
                    else -> if (ascending) games.sortedBy { it.finishTimestamp } else games.sortedByDescending { it.finishTimestamp }
                }
            }
            Divider()
            Divider()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortedGames) { g ->
                    val names = g.winners.mapNotNull { winnerNames[it] }
                    val subtitleText = if (names.size == g.winners.size) {
                        "Players: ${g.totalPlayers} · Winners: ${names.joinToString()}"
                    } else {
                        "Players: ${g.totalPlayers} · Winners: \u2014"
                    }
                    GameHistoryItem(
                        title = "${g.playerMode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${g.gameMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        subtitle = subtitleText,
                        time = (g.finishTimestamp - g.creationTimestamp).coerceAtLeast(0L),
                        onClick = { onOpenDetail(g) }
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
private fun GameHistoryItem(title: String, subtitle: String, time: Long, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Duration: ${formatElapsedTimeMs(time)}", style = MaterialTheme.typography.labelLarge)
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

@Preview
@Composable
private fun GameHistoryItemPreview() {
    GameHistoryItem(
        title = "Solo · Normal",
        subtitle = "Players: 3 · Winners: Alice, Bob",
        time = 125_000L,
        onClick = {}
    )
}


