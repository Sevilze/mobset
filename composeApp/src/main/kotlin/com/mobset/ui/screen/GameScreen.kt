package com.mobset.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.domain.model.*
import com.mobset.ui.component.SetCard
import com.mobset.ui.viewmodel.GameViewModel
import com.mobset.ui.util.formatElapsedTimeMs
import com.mobset.ui.component.FoundSetsPanel
import com.mobset.domain.algorithm.SetAlgorithms

import kotlinx.coroutines.delay


/**
 * Main game screen where the Set game is played.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameScreen(
    gameMode: GameMode,
    hintsEnabled: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = hiltViewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val gameResult by viewModel.gameResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var hintedCards by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Start game when screen is first composed
    LaunchedEffect(gameMode, hintsEnabled) {
        if (gameState.gameStatus == GameStatus.NOT_STARTED) {
            viewModel.startNewGame(gameMode, hintsEnabled)
        }
    }

    // Handle game results
    LaunchedEffect(gameResult) {
        val result = gameResult
        when (result) {
            is GameResult.Hint -> {
                hintedCards = result.cardIndices
                delay(3000) // Show hint for 3 seconds
                hintedCards = emptyList()
                viewModel.clearGameResult()
            }

            is GameResult.SetFound -> {
                // Clear immediately for snappy UX
                viewModel.clearGameResult()
            }

            is GameResult.InvalidSet -> {
                // Show snackbar near bottom above the panel
                snackbarHostState.showSnackbar(
                    "Not a valid set. Try again!",
                    withDismissAction = true
                )
                viewModel.clearGameResult()
            }

            is GameResult.NoSetsAvailable -> {
                snackbarHostState.showSnackbar("No sets available. Deal more cards.")
                viewModel.clearGameResult()
            }

            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = gameMode.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {

                    IconButton(
                        onClick = { viewModel.useHint() },
                        enabled = gameState.gameStatus == GameStatus.IN_PROGRESS && hintsEnabled
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Hint")
                    }
                    IconButton(
                        onClick = { viewModel.dealCards() },
                        enabled = gameState.gameStatus == GameStatus.IN_PROGRESS
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Deal Cards")
                    }
                }
            )
        },
        bottomBar = {
            FoundSetsPanel(
                status = gameState.gameStatus,
                mode = gameState.mode,
                foundSets = gameState.foundSets,
                showTimestamps = false,
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Game status and info
            GameStatusCard(
                gameState = gameState,
                gameResult = gameResult,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game board
            when (gameState.gameStatus) {
                GameStatus.NOT_STARTED -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                GameStatus.IN_PROGRESS -> {
                    GameBoard(
                        board = gameState.board,
                        selectedCards = gameState.selectedCards,
                        hintedCards = hintedCards,
                        onCardClick = { index -> viewModel.selectCard(index) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                GameStatus.COMPLETED -> {
                    // Keep the final board visible and overlay the dialog
                    Box(modifier = Modifier.fillMaxSize()) {
                        GameBoard(
                            board = gameState.board,
                            selectedCards = emptySet(),
                            hintedCards = emptyList(),
                            onCardClick = {},
                            modifier = Modifier.fillMaxSize()
                        )
                        AlertDialog(
                            onDismissRequest = onNavigateBack,
                            title = { Text("Game Completed") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Final Time: ${formatElapsedTimeMs(gameState.elapsedTime)}")
                                    Text("Sets Found: ${gameState.foundSets.size}")
                                    Text("Hints Used: ${gameState.hintsUsed}")
                                }
                            },
                            confirmButton = {
                                Button(onClick = { viewModel.startNewGame(gameMode, hintsEnabled) }) {
                                    Text("Play Again")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = onNavigateBack) {
                                    Text("Home")
                                }
                            }
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Game Error")
                    }
                }
            }
        }
    }
}

/**
 * Displays the current game status and feedback.
 */
@Composable
private fun GameStatusCard(
    gameState: GameState,
    gameResult: GameResult?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (gameResult) {
                is GameResult.SetFound -> MaterialTheme.colorScheme.primaryContainer
                is GameResult.InvalidSet -> MaterialTheme.colorScheme.errorContainer
                is GameResult.Hint -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Stylized timer replaces Sets/Hints columns
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = formatElapsedTimeMs(gameState.elapsedTime),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Displays the game board with cards in a grid.
 */
@Composable
private fun GameBoard(
    board: List<Card>,
    selectedCards: Set<Int>,
    hintedCards: List<Int>,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // Fixed 3 columns for proper Set game layout
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(board) { index, card ->
            SetCard(
                card = card,
                isSelected = selectedCards.contains(index),
                isHinted = hintedCards.contains(index),
                onClick = { onCardClick(index) }
            )
        }
    }
}

/**
 * Displays the game completion screen.
 */
@Composable
private fun GameCompletedScreen(
    gameState: GameState,
    onNewGame: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Game Completed!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Final Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatElapsedTimeMs(gameState.elapsedTime),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sets Found: ${gameState.foundSets.size}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Hints Used: ${gameState.hintsUsed}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNewGame,
                modifier = Modifier.weight(1f)
            ) {
                Text("Play Again")
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Home")
            }
        }
    }
}

@Composable
@Preview
fun GameStatusCardPreview() {
    GameStatusCard(
        gameState = GameState(
            mode = GameMode.NORMAL,
            board = SetAlgorithms.generateDeck(GameMode.NORMAL).take(12),
            gameStatus = GameStatus.IN_PROGRESS,
            elapsedTime = 123_456
        ),
        gameResult = null
    )
}

