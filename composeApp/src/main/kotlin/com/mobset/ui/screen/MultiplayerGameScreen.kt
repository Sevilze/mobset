package com.mobset.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.domain.model.*
import com.mobset.ui.component.FoundSetsPanel
import com.mobset.ui.component.SetCard
import com.mobset.ui.util.formatElapsedTimeMs
import com.mobset.ui.viewmodel.MultiplayerGameViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiplayerGameScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MultiplayerGameViewModel = hiltViewModel()
) {
    LaunchedEffect(roomId) { viewModel.setRoom(roomId) }

    val gameState by viewModel.gameState.collectAsState()
    val gameResult by viewModel.gameResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCompletion by rememberSaveable { mutableStateOf(false) }
    var completionShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gameState.gameStatus) {
        if (gameState.gameStatus == GameStatus.COMPLETED && !completionShown) {
            showCompletion = true
            completionShown = true
        }
    }

    // Disable back navigation until game completion
    val backEnabled = gameState.gameStatus == GameStatus.COMPLETED
    BackHandler(enabled = !backEnabled) { /* block */ }

    // Handle transient results
    LaunchedEffect(gameResult) {
        when (val r = gameResult) {
            is GameResult.InvalidSet -> {
                snackbarHostState.showSnackbar(
                    message = "Not a valid set. Try again!",
                    withDismissAction = true
                )
                viewModel.clearGameResult()
            }
            is GameResult.SetFound -> viewModel.clearGameResult()
            else -> {}
        }
    }

    Scaffold(
        bottomBar = {
            FoundSetsPanel(
                status = gameState.gameStatus,
                mode = gameState.mode,
                foundSets = gameState.foundSets,
                showTimestamps = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 8.dp))
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = formatElapsedTimeMs(gameState.elapsedTime),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (gameState.gameStatus) {
                GameStatus.NOT_STARTED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                GameStatus.IN_PROGRESS, GameStatus.PAUSED -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(gameState.board) { index, card ->
                            SetCard(
                                card = card,
                                isSelected = gameState.selectedCards.contains(index),
                                isHinted = false,
                                onClick = { viewModel.selectCard(index) }
                            )
                        }
                    }
                }
                GameStatus.COMPLETED -> {
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(gameState.board) { index, card ->
                                SetCard(card = card, isSelected = false, isHinted = false, onClick = {})
                            }
                        }
                        if (showCompletion) {
                            AlertDialog(
                                onDismissRequest = {
                                    showCompletion = false
                                    onNavigateBack()
                                },
                                title = { Text("Game Completed") },
                                text = { Text("Final Time: ${formatElapsedTimeMs(gameState.elapsedTime)}") },
                                confirmButton = {
                                    Button(onClick = {
                                        showCompletion = false
                                        onNavigateBack()
                                    }) { Text("OK") }
                                }
                            )
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error") }
            }
        }
    }
}

@Preview
@Composable
private fun MultiplayerGameScreenPreview() {
    MultiplayerGameScreen(roomId = "demo-room", onNavigateBack = { })
}

