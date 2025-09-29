package com.mobset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.data.history.GameRecord
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.GameStatus
import com.mobset.ui.component.FoundSetsPanel
import com.mobset.ui.viewmodel.ProfileViewModel

import com.mobset.ui.util.formatElapsedTimeMs
import com.mobset.ui.component.SetCard

import com.mobset.domain.model.Card
import com.mobset.domain.model.SetType
import com.mobset.domain.model.FoundSet
import androidx.compose.ui.tooling.preview.Preview
import com.mobset.domain.algorithm.SetAlgorithms

import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryDetailScreen(onNavigateBack: () -> Unit, vm: ProfileViewModel) {
    val record = vm.selectedGame.collectAsState().value
    val winnerNames by vm.winnerNames.collectAsState()

    val displayDate = remember(record?.finishTimestamp) {
        record?.finishTimestamp?.let { SimpleDateFormat("dd/MM/yyyy, HH:mm").format(Date(it)) } ?: ""
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Game details") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        )
    }) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No game selected", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${record.playerMode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${record.gameMode.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge)
                    Text("Finished: $displayDate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val duration = (record.finishTimestamp - record.creationTimestamp).coerceAtLeast(0L)
                    Text("Duration: ${formatElapsedTimeMs(duration)}", style = MaterialTheme.typography.bodyMedium)
                    val names = record.winners.mapNotNull { id -> vm.winnerNames.value[id] }
                    val winnersText = if (names.size == record.winners.size) names.joinToString() else "\u2014"
                    Text("Winners: $winnersText", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Found sets panel (history)
            val mode = if (record.gameMode.name.equals("ULTRA", ignoreCase = true)) GameMode.ULTRA else GameMode.NORMAL
            val foundSets = remember(record, winnerNames) { record.toDomainFoundSets(mode, winnerNames) }
            FoundSetsPanel(
                status = GameStatus.COMPLETED,
                mode = mode,
                foundSets = foundSets,
                showTimestamps = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Final board reconstruction
            val finalBoard = remember(record) { reconstructFinalBoard(record, mode) }
            if (finalBoard.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Final board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        finalBoard.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { c -> SetCard(card = c, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(48.dp)) }
                            }
                        }
                    }
                }

            }
        }
    }
}

// Show the final board exactly as persisted. Do not attempt to reconstruct.
private fun reconstructFinalBoard(
    record: GameRecord,
    mode: GameMode
): List<Card> {
    return record.finalBoardEncodings.map { Card(it) }
}

// Map history events to domain FoundSet using encodings; attach display name via ProfileViewModel cache
private fun GameRecord.toDomainFoundSets(mode: GameMode, winnerNames: Map<String, String>): List<FoundSet> {
    return this.setsFoundHistory.map { e ->
        val cards = e.cardEncodings.map { Card(it) }

        val type = if (mode == GameMode.ULTRA || cards.size == SetType.ULTRA.size) SetType.ULTRA else SetType.NORMAL
        val name = winnerNames[e.playerId]
        FoundSet(cards = cards, type = type, foundBy = name, timestamp = e.timestamp)
    }
}


@Preview
@Composable
private fun FoundSetsPanelPreview() {
    val mode = GameMode.NORMAL
    val fs = listOf(
        FoundSet(cards = SetAlgorithms.generateDeck(mode).take(3), type = SetType.NORMAL, foundBy = "Alice")
    )
    FoundSetsPanel(status = GameStatus.COMPLETED, mode = mode, foundSets = fs, showTimestamps = true, modifier = Modifier.fillMaxWidth())
}
