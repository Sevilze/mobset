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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobset.domain.model.*
import com.mobset.ui.component.SetCard
import com.mobset.ui.viewmodel.GameViewModel
import kotlinx.coroutines.delay

/**
 * Main game screen where the Set game is played.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameMode: GameMode,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val gameResult by viewModel.gameResult.collectAsStateWithLifecycle()
    
    var hintedCards by remember { mutableStateOf<List<Int>>(emptyList()) }
    
    // Start game when screen is first composed
    LaunchedEffect(gameMode) {
        if (gameState.gameStatus == GameStatus.NOT_STARTED) {
            viewModel.startNewGame(gameMode)
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
                delay(1000) // Brief pause to show success
                viewModel.clearGameResult()
            }
            is GameResult.InvalidSet -> {
                delay(1000) // Brief pause to show error
                viewModel.clearGameResult()
            }
            else -> {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = gameMode.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Score: ${gameState.score}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.useHint() },
                        enabled = gameState.gameStatus == GameStatus.IN_PROGRESS
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
                    GameCompletedScreen(
                        gameState = gameState,
                        onNewGame = { viewModel.startNewGame(gameMode) },
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sets Found: ${gameState.foundSets.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Hints Used: ${gameState.hintsUsed}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            AnimatedVisibility(
                visible = gameResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                when (gameResult) {
                    is GameResult.SetFound -> {
                        Text(
                            text = "Great! Valid set found! +${calculateSetScore(gameResult.foundSet.type)} points",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is GameResult.InvalidSet -> {
                        Text(
                            text = "Not a valid set. Try again!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is GameResult.Hint -> {
                        Text(
                            text = "Hint: Look at the highlighted cards",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is GameResult.NoSetsAvailable -> {
                        Text(
                            text = "No sets available. Deal more cards!",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    else -> {}
                }
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
        columns = GridCells.Adaptive(minSize = 80.dp),
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
                    text = "Final Score",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${gameState.score}",
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

private fun calculateSetScore(setType: SetType): Int {
    return when (setType) {
        SetType.NORMAL -> 10
        SetType.ULTRA -> 15
        SetType.FOUR_SET -> 20
        SetType.GHOST -> 25
    }
}
