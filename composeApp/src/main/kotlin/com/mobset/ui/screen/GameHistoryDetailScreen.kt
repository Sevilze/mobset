package com.mobset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.data.history.GameRecord
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.Card
import com.mobset.domain.model.FoundSet
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.GameStatus
import com.mobset.domain.model.SetType
import com.mobset.ui.component.FoundSetsPanel
import com.mobset.ui.component.SetCard
import com.mobset.ui.util.formatElapsedTimeMs
import com.mobset.ui.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameHistoryDetailScreen(onNavigateBack: () -> Unit, vm: ProfileViewModel) {
    val record = vm.selectedGame.collectAsState().value
    val winnerNames by vm.winnerNames.collectAsState()

    val displayDate =
        remember(record?.finishTimestamp) {
            record?.finishTimestamp?.let { SimpleDateFormat("dd/MM/yyyy, HH:mm").format(Date(it)) }
                ?: ""
        }
    val mode =
        remember(record) {
            if (record?.gameMode?.name.equals(
                    "ULTRA",
                    ignoreCase = true
                )
            ) {
                GameMode.ULTRA
            } else {
                GameMode.NORMAL
            }
        }
    val foundSets =
        remember(record, winnerNames) {
            if (record != null) record.toDomainFoundSets(mode, winnerNames) else emptyList()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            FoundSetsPanel(
                status = GameStatus.COMPLETED,
                mode = mode,
                foundSets = foundSets,
                showTimestamps = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No game selected", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val duration =
            remember(record) {
                (record.finishTimestamp - record.creationTimestamp).coerceAtLeast(0L)
            }
        val finalBoard = remember(record, mode) { reconstructFinalBoard(record, mode) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            ReplayMetaCard(
                finalTimeText = formatElapsedTimeMs(duration),
                dateText = displayDate,
                modeText =
                record.gameMode.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full board layout (view-only), mirroring game screen grid
            if (finalBoard.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(finalBoard) { _, c ->
                        SetCard(card = c, isSelected = false, onClick = {}, isHinted = false)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No board available")
                }
            }
        }
    }
}

@Composable
private fun ReplayMetaCard(finalTimeText: String, dateText: String, modeText: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = finalTimeText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$dateText · $modeText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Show the final board exactly as persisted. Do not attempt to reconstruct.
private fun reconstructFinalBoard(record: GameRecord, mode: GameMode): List<Card> =
    record.finalBoardEncodings.map { Card(it) }

// Map history events to domain FoundSet using encodings; attach display name via ProfileViewModel cache
private fun GameRecord.toDomainFoundSets(
    mode: GameMode,
    winnerNames: Map<String, String>
): List<FoundSet> = this.setsFoundHistory.map { e ->
    val cards = e.cardEncodings.map { Card(it) }

    val type = if (mode == GameMode.ULTRA ||
        cards.size == SetType.ULTRA.size
    ) {
        SetType.ULTRA
    } else {
        SetType.NORMAL
    }
    val name = winnerNames[e.playerId]
    FoundSet(cards = cards, type = type, foundBy = name, timestamp = e.timestamp)
}

@Preview
@Composable
private fun FoundSetsPanelPreview() {
    val mode = GameMode.NORMAL
    val fs =
        listOf(
            FoundSet(
                cards = SetAlgorithms.generateDeck(mode).take(3),
                type = SetType.NORMAL,
                foundBy = "Alice"
            )
        )
    FoundSetsPanel(
        status = GameStatus.COMPLETED,
        mode = mode,
        foundSets = fs,
        showTimestamps = true,
        modifier = Modifier.fillMaxWidth()
    )
}
