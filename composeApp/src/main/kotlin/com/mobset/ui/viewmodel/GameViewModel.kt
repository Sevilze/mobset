package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull


/**


 * ViewModel for managing Set game state and logic.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class GameViewModel @javax.inject.Inject constructor(
    private val authRepository: com.mobset.data.auth.AuthRepository,
    private val historyRepository: com.mobset.data.history.GameHistoryRepository
) : ViewModel() {

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult.asStateFlow()

    private var timerJob: Job? = null

    /**
     * Starts a new game with the specified mode.
     */
    fun startNewGame(mode: GameMode) {
        viewModelScope.launch {
            val deck = SetAlgorithms.generateDeck(mode)
            val setType = mode.setTypes.first()
            val board = SetAlgorithms.findBoard(deck, mode, setType)

            val currentTime = System.currentTimeMillis()
            _gameState.value = GameState(
                gameId = generateGameId(),
                mode = mode,
                deck = deck,
                board = board,
                selectedCards = emptySet(),
                foundSets = emptyList(),
                usedCards = emptySet(),
                startTime = currentTime,
                elapsedTime = 0,
                hintsUsed = 0,
                gameStatus = GameStatus.IN_PROGRESS,
                lastAction = GameAction.NewGame,
                timestamp = currentTime
            )

            _gameResult.value = null

            // Start the live timer
            startTimer()
        }
    }

    /**
     * Handles card selection/deselection.
     */
    fun selectCard(cardIndex: Int) {
        val currentState = _gameState.value
        if (currentState.gameStatus != GameStatus.IN_PROGRESS) return

        val selectedCards = currentState.selectedCards.toMutableSet()
        val setType = currentState.mode.setTypes.first()

        if (selectedCards.contains(cardIndex)) {
            // Deselect card
            selectedCards.remove(cardIndex)
            _gameState.value = currentState.copy(
                selectedCards = selectedCards,
                lastAction = GameAction.DeselectCard(cardIndex)
            )
        } else {
            // Select card
            selectedCards.add(cardIndex)

            // Check if we have enough cards for a set
            if (selectedCards.size == setType.size) {
                checkSelectedSet(selectedCards.toList(), currentState)
            } else {
                _gameState.value = currentState.copy(
                    selectedCards = selectedCards,
                    lastAction = GameAction.SelectCard(cardIndex)
                )
            }
        }
    }

    /**
     * Checks if the selected cards form a valid set.
     */
    private fun checkSelectedSet(selectedIndices: List<Int>, currentState: GameState) {
        val selectedCards = selectedIndices.map { currentState.board[it] }
        val setType = currentState.mode.setTypes.first()

        val isValidSet = when (setType) {
            SetType.NORMAL -> SetAlgorithms.checkSetNormal(selectedCards, currentState.mode)
            SetType.ULTRA -> SetAlgorithms.checkSetUltra(selectedCards, currentState.mode)
            SetType.FOUR_SET -> SetAlgorithms.checkSet4Set(selectedCards, currentState.mode)
            SetType.GHOST -> SetAlgorithms.checkSetGhost(selectedCards, currentState.mode)
        }

        if (isValidSet) {
            // Valid set found
            val foundSet = FoundSet(
                cards = selectedCards,
                type = setType,
                timestamp = System.currentTimeMillis()
            )

            val newFoundSets = currentState.foundSets + foundSet
            val currentTime = System.currentTimeMillis()
            val newElapsedTime = currentTime - currentState.startTime
            val newUsedCards = currentState.usedCards + selectedCards.toSet()

            // Remove found cards and update board
            val (newDeck, newBoardSize) = removeCardsAndUpdateBoard(selectedCards, currentState.deck, currentState.mode, newUsedCards)
            val newBoard = newDeck.take(newBoardSize)

            _gameState.value = currentState.copy(
                deck = newDeck,
                board = newBoard,
                selectedCards = emptySet(),
                foundSets = newFoundSets,
                usedCards = newUsedCards,
                elapsedTime = newElapsedTime,
                lastAction = GameAction.CheckSet
            )

            _gameResult.value = GameResult.SetFound(foundSet)

            // Check if game is completed
            checkGameCompletion()
        } else {
            // Invalid set
            _gameState.value = currentState.copy(
                selectedCards = emptySet(),
                lastAction = GameAction.CheckSet
            )

            _gameResult.value = GameResult.InvalidSet(selectedCards)
        }
    }

    /**
     * Provides a hint by highlighting cards that form a valid set.
     */
    fun useHint() {
        val currentState = _gameState.value
        if (currentState.gameStatus != GameStatus.IN_PROGRESS) return

        val setType = currentState.mode.setTypes.first()
        val availableSets = SetAlgorithms.findSets(currentState.board, setType, currentState.mode)

        if (availableSets.isNotEmpty()) {
            val hintSet = availableSets.first()
            _gameState.value = currentState.copy(
                hintsUsed = currentState.hintsUsed + 1,
                lastAction = GameAction.UseHint
            )
            _gameResult.value = GameResult.Hint(hintSet)
        } else {
            _gameResult.value = GameResult.NoSetsAvailable
        }
    }

    /**
     * Deals additional cards to the board.
     */
    fun dealCards() {
        val currentState = _gameState.value
        if (currentState.gameStatus != GameStatus.IN_PROGRESS) return

        // Deal 3 more cards manually (user requested) - just increase board size
        val currentBoardSize = currentState.board.size
        val newBoardSize = minOf(currentBoardSize + 3, currentState.deck.size)
        val newBoard = currentState.deck.take(newBoardSize)

        _gameState.value = currentState.copy(
            board = newBoard,
            selectedCards = emptySet(),
            lastAction = GameAction.DealCards
        )
    }

    /**
     * Clears the current selection.
     */
    fun clearSelection() {
        val currentState = _gameState.value
        _gameState.value = currentState.copy(
            selectedCards = emptySet(),
            lastAction = GameAction.ClearSelection
        )
    }

    /**
     * Clears the current game result.
     */
    fun clearGameResult() {
        _gameResult.value = null
    }

    /**
     * Removes cards from deck and updates board size.
     */
    private fun removeCardsAndUpdateBoard(
        cardsToRemove: List<Card>,
        currentDeck: List<Card>,
        mode: GameMode,
        usedCards: Set<Card>
    ): Pair<List<Card>, Int> {
        val mutableDeck = currentDeck.toMutableList()
        val currentBoardSize = currentDeck.size.coerceAtMost(currentDeck.size)
        val minBoardSize = mode.boardSize

        if (cardsToRemove.size == currentBoardSize) {
            repeat(currentBoardSize) {
                if (mutableDeck.isNotEmpty()) {
                    mutableDeck.removeAt(0)
                }
            }
        } else {
            val cutoff = minOf(mutableDeck.size - cardsToRemove.size, minBoardSize)

            // Get card indices and sort descending
            val cardIndices = cardsToRemove.mapNotNull { card ->
                mutableDeck.indexOf(card).takeIf { it >= 0 }
            }.sortedDescending()

            for ((i, cardIndex) in cardIndices.withIndex()) {
                if (cardIndex >= cutoff) {
                    mutableDeck.removeAt(cardIndex)
                } else {
                    val remainingToRemove = cardIndices.size - i
                    if (cutoff + remainingToRemove <= mutableDeck.size) {
                        // Take cards from beyond cutoff to replace the found cards
                        val replacementCards = mutableDeck.subList(cutoff, cutoff + remainingToRemove).toList()

                        // Replace found cards with replacement cards
                        for ((j, replacement) in replacementCards.withIndex()) {
                            val targetIndex = cardIndices[cardIndices.size - 1 - j]
                            if (targetIndex < mutableDeck.size) {
                                mutableDeck[targetIndex] = replacement
                            }
                        }

                        // Remove the replacement cards from their original positions
                        repeat(remainingToRemove) {
                            if (mutableDeck.size > cutoff) {
                                mutableDeck.removeAt(cutoff)
                            }
                        }
                    }
                    break
                }
            }
        }

        // Find new board size
        val newBoardSize = adjustBoardSize(mutableDeck, mode, minBoardSize)

        return Pair(mutableDeck, newBoardSize)
    }

    /**
     * Adjust the board size to increment when there are no sets available.
     */
    private fun adjustBoardSize(
        currentDeck: List<Card>,
        mode: GameMode,
        minBoardSize: Int
    ): Int {
        val setType = mode.setTypes.first()
        val maxPossibleSize = currentDeck.size
        var boardSize = minOf(maxPossibleSize, minBoardSize)

        // Increase by 3 until we find a set or exhaust deck
        while (boardSize < maxPossibleSize) {
            val testBoard = currentDeck.take(boardSize)
            val availableSets = SetAlgorithms.findSets(testBoard, setType, mode)
            if (availableSets.isNotEmpty()) {
                return boardSize
            }

            boardSize += 3
        }

        return maxPossibleSize
    }

    private fun checkGameCompletion() {
        val currentState = _gameState.value
        val setType = currentState.mode.setTypes.first()
        val availableSets = SetAlgorithms.findSets(currentState.board, setType, currentState.mode)

        // Game is completed when no sets are available and deck is exhausted
        if (availableSets.isEmpty() && currentState.board.size >= currentState.deck.size) {
            stopTimer()
            _gameState.value = currentState.copy(gameStatus = GameStatus.COMPLETED)
            _gameResult.value = GameResult.GameCompleted
            // Persist game completion asynchronously
            viewModelScope.launch { persistGameCompletion() }
        }
    }

    private suspend fun persistGameCompletion() {
        val state = _gameState.value
        val user = authRepository.currentUser.firstOrNull()
        val uid = user?.uid ?: return
        // Build record
        val modeType = when (state.mode.id) {
            "ultra" -> com.mobset.data.history.GameModeType.ULTRA
            else -> com.mobset.data.history.GameModeType.NORMAL
        }
        val setsHistory = state.foundSets.map { fs ->
            com.mobset.data.history.SetFoundEvent(
                playerId = uid,
                timestamp = fs.timestamp,
                cardEncodings = fs.cards.map { it.encoding }
            )
        }
        val playerStats = listOf(
            com.mobset.data.history.PlayerGameStats(
                playerId = uid,
                setsFound = state.foundSets.size,
                timeMs = state.elapsedTime
            )
        )
        val rec = com.mobset.data.history.GameRecord(
            gameId = state.gameId,
            creationTimestamp = state.startTime,
            finishTimestamp = state.startTime + state.elapsedTime,
            hostPlayerId = uid,
            totalPlayers = 1,
            gameMode = modeType,
            playerMode = com.mobset.data.history.PlayerMode.SOLO,
            winners = listOf(uid),
            setsFoundHistory = setsHistory,
            playerStats = playerStats
        )
        kotlin.runCatching { historyRepository.addGameRecord(rec) }
    }

    private fun generateGameId(): String {
        return "game_${System.currentTimeMillis()}"
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val currentState = _gameState.value
                if (currentState.gameStatus == GameStatus.IN_PROGRESS) {
                    val currentTime = System.currentTimeMillis()
                    val newElapsedTime = currentTime - currentState.startTime

                    _gameState.value = currentState.copy(elapsedTime = newElapsedTime)
                }
                delay(10)
            }
        }
    }

    /**
     * Stops the live timer.
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
