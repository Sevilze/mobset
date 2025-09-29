package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.history.GameEvent
import com.mobset.data.history.GameModeType
import com.mobset.data.history.GameRecord
import com.mobset.data.history.PlayerGameStats
import com.mobset.data.history.PlayerMode
import com.mobset.data.history.SetFoundEvent
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * ViewModel for managing Set game state and logic.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: GameHistoryRepository
) : ViewModel() {

    private val currentDisplayName = MutableStateFlow<String?>(null)
    private val currentUid = MutableStateFlow<String?>(null)

    // Full-state capture for history
    private var gameSeed: Long? = null
    private var initialDeckEncodings: List<String> = emptyList()
    private val events = mutableListOf<GameEvent>()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect {
                currentDisplayName.value = it?.displayName
                currentUid.value = it?.uid
            }
        }
    }


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
            // Deterministic deck with seed; also record full-state history scaffolding
            val seed = kotlin.random.Random.nextLong()
            gameSeed = seed
            val deck = SetAlgorithms.generateDeck(mode, seed)
            initialDeckEncodings = deck.map { it.encoding }
            events.clear()

            val setType = mode.setTypes.first()
            val board = SetAlgorithms.findBoard(deck, mode, setType)
            val startIndex = (0..(deck.size - board.size)).firstOrNull { i ->
                deck.subList(i, i + board.size) == board
            } ?: 0
            val reorderedDeck = if (startIndex == 0) deck else buildList(deck.size) {
                addAll(board)
                addAll(deck.take(startIndex))
                addAll(deck.drop(startIndex + board.size))
            }

            val currentTime = System.currentTimeMillis()
            _gameState.value = GameState(
                gameId = generateGameId(),
                mode = mode,
                deck = reorderedDeck,
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

            initialDeckEncodings = reorderedDeck.map { it.encoding }

            // Record a start event and initial deal size for precise reconstruction
            events += GameEvent(
                type = "start",
                timestamp = currentTime,
                playerId = currentUid.value,
                boardSize = board.size
            )

            _gameResult.value = null

            // Persist a provisional record immediately so history appears during play
            val uid = currentUid.value
            if (uid != null) {
                val modeType = when (mode.id) {
                    "ultra" -> GameModeType.ULTRA
                    else -> GameModeType.NORMAL
                }
                val provisional = GameRecord(
                    gameId = _gameState.value.gameId,
                    creationTimestamp = currentTime,
                    finishTimestamp = currentTime,
                    hostPlayerId = uid,
                    totalPlayers = 1,
                    gameMode = modeType,
                    playerMode = PlayerMode.SOLO,
                    winners = emptyList(),
                    setsFoundHistory = emptyList(),
                    playerStats = listOf(
                        PlayerGameStats(
                            playerId = uid, setsFound = 0, timeMs = 0
                        )
                    )
                )
                viewModelScope.launch {
                    try {
                        historyRepository.addGameRecord(provisional)
                    } catch (t: Throwable) {
                        android.util.Log.e("GameVM", "addGameRecord(provisional) failed", t)
                    }
                }
            }

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
            viewModelScope.launch {
                // Valid set found
                val foundSet = FoundSet(
                    cards = selectedCards,
                    type = setType,
                    foundBy = currentDisplayName.value,
                    timestamp = System.currentTimeMillis()
                )

                val newFoundSets = currentState.foundSets + foundSet
                val currentTime = System.currentTimeMillis()
                val newElapsedTime = currentTime - currentState.startTime
                val newUsedCards = currentState.usedCards + selectedCards.toSet()

                // Remove found cards and update board (off main thread to avoid UI stutter)
                val (newDeck, newBoardSize) = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    removeCardsAndUpdateBoard(
                        selectedCards,
                        currentState.deck,
                        currentState.board.size,
                        currentState.mode,
                        newUsedCards
                    )
                }
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

                // Record event for full-state reconstruction
                events += GameEvent(
                    type = "set",
                    timestamp = foundSet.timestamp,
                    playerId = currentUid.value,
                    cardEncodings = selectedCards.map { it.encoding },
                    boardSize = newBoard.size
                )

                _gameResult.value = GameResult.SetFound(foundSet)

                // Check if game is completed
                checkGameCompletion()
            }
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
            // Record hint event
            events += GameEvent(
                type = "hint",
                timestamp = System.currentTimeMillis(),
                playerId = currentUid.value,
                cardEncodings = hintSet.map { idx -> currentState.board[idx].encoding },
                boardSize = currentState.board.size
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
        // Record deal event
        events += GameEvent(
            type = "deal",
            timestamp = System.currentTimeMillis(),
            playerId = currentUid.value,
            boardSize = newBoardSize
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
        currentBoardSize: Int,
        mode: GameMode,
        usedCards: Set<Card>
    ): Pair<List<Card>, Int> {
        val mutableDeck = currentDeck.toMutableList()
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
                        val replacementCards =
                            mutableDeck.subList(cutoff, cutoff + remainingToRemove).toList()

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
            "ultra" -> GameModeType.ULTRA
            else -> GameModeType.NORMAL
        }
        val setsHistory = state.foundSets.map { fs ->
            SetFoundEvent(
                playerId = uid,
                timestamp = fs.timestamp,
                cardEncodings = fs.cards.map { it.encoding }
            )
        }
        val playerStats = listOf(
            PlayerGameStats(
                playerId = uid,
                setsFound = state.foundSets.size,
                timeMs = state.elapsedTime
            )
        )


        val rec = GameRecord(
            gameId = state.gameId,
            creationTimestamp = state.startTime,
            finishTimestamp = state.startTime + state.elapsedTime,
            hostPlayerId = uid,
            totalPlayers = 1,
            gameMode = modeType,
            playerMode = PlayerMode.SOLO,
            winners = listOf(uid),
            setsFoundHistory = setsHistory,
            playerStats = playerStats,
            seed = gameSeed,
            initialDeckEncodings = initialDeckEncodings,
            events = events.toList(),
            finalBoardEncodings = state.board.map { it.encoding }
        )
        try {
            historyRepository.updateGameRecord(rec)
        } catch (t: Throwable) {
            android.util.Log.e("GameVM", "updateGameRecord(final) failed", t)
        }
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

    private fun computeFinalBoardEncodingsFromEvents(
        mode: GameMode,
        initialDeckEncodings: List<String>,
        seed: Long?,
        events: List<GameEvent>
    ): List<String> {
        val initial: List<Card> = if (initialDeckEncodings.isNotEmpty()) {
            initialDeckEncodings.map { Card(it) }
        } else {
            SetAlgorithms.generateDeck(mode, seed)
        }
        val current = java.util.LinkedHashSet(initial)
        events.sortedBy { it.timestamp }.forEach { e ->
            if (e.type == "set") {
                val enc = e.cardEncodings ?: emptyList()
                enc.forEach { current.remove(Card(it)) }
            }
        }
        val minBoardSize = mode.boardSize
        val currentList = current.toList()
        val board = mutableListOf<Card>()
        var idx = 0
        while (idx < currentList.size && board.size < minBoardSize) {
            board.add(currentList[idx++])
        }
        while (idx < currentList.size) {
            val setType = mode.setTypes.first()
            if (SetAlgorithms.findSets(board, setType, mode).isNotEmpty()) break
            val toAdd = 3 - (board.size % 3)
            repeat(toAdd.coerceAtMost(currentList.size - idx)) { board.add(currentList[idx++]) }
        }
        if (board.isEmpty()) return currentList.take(minBoardSize).map { it.encoding }
        return board.map { it.encoding }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
