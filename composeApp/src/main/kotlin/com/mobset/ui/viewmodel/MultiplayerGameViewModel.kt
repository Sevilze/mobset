package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.multiplayer.MultiplayerGameData
import com.mobset.data.multiplayer.MpEvent
import com.mobset.data.multiplayer.MultiplayerGameRepository
import com.mobset.data.profile.ProfileRepository
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.history.GameRecord
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import com.mobset.data.history.SetFoundEvent
import com.mobset.data.history.GameEvent
import com.mobset.data.history.PlayerGameStats
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerGameViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val repo: MultiplayerGameRepository,
    private val profiles: ProfileRepository,
    private val historyRepository: GameHistoryRepository,
) : ViewModel() {

    private val roomId = MutableStateFlow<String?>(null)

    private val gameData: StateFlow<MultiplayerGameData?> = roomId
        .filterNotNull()
        .flatMapLatest { id -> repo.observeGame(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult

    private var completionMarked = false
    private var historyPersisted = false

    private var timerJob: Job? = null

    private fun startTimer(startedAt: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _gameState.value.gameStatus == GameStatus.IN_PROGRESS) {
                val now = System.currentTimeMillis()
                val current = _gameState.value
                _gameState.value = current.copy(
                    elapsedTime = (now - startedAt).coerceAtLeast(0L)
                )
                delay(50)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // uid->display name cache
    private val names = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // Rebuild local game state when remote data changes
        viewModelScope.launch {
            gameData.collect { data ->
                if (data == null) return@collect
                val mode = GameMode.fromId(data.mode) ?: GameMode.NORMAL
                // Wait for authoritative start time to avoid flickering different initial boards
                if (data.startedAt == null || data.status != "ingame") {
                    stopTimer()
                    _gameState.value = GameState(
                        gameId = data.roomId,
                        mode = mode,
                        deck = emptyList(),
                        board = emptyList(),
                        selectedCards = emptySet(),
                        foundSets = emptyList(),
                        usedCards = emptySet(),
                        startTime = data.startedAt ?: 0L,
                        elapsedTime = 0L,
                        hintsUsed = 0,
                        gameStatus = if (data.status == "completed") GameStatus.COMPLETED else GameStatus.NOT_STARTED,
                        lastAction = null,
                        timestamp = System.currentTimeMillis()
                    )
                    return@collect
                }

                val seed = data.startedAt ?: 0L
                val fullDeck = SetAlgorithms.generateDeck(mode, seed)
                var deck = fullDeck.toMutableList()

                // Process events in time order
                val foundSets = mutableListOf<FoundSet>()
                data.events.sortedBy { it.time }.forEach { e ->
                    val cards = e.cards.map { Card(it) }
                    val before = deck.toList()
                    val (newDeck, newBoardSize) = removeCardsAndUpdateBoard(
                        cardsToRemove = cards,
                        currentDeck = deck,
                        currentBoardSize = deck.take(mode.boardSize).size.coerceAtLeast(mode.boardSize),
                        mode = mode,
                        usedCards = emptySet()
                    )
                    if (newDeck != deck) {
                        // Accepted event
                        deck = newDeck.toMutableList()
                        val name = names.value[e.userId]
                        foundSets += FoundSet(cards = cards, type = mode.setTypes.first(), foundBy = name, timestamp = e.time)
                    } else {
                        // Ignore invalid/conflicting event
                    }
                }

                // Build board from deck
                val setType = mode.setTypes.first()
                val boardSize = computeBoardSize(deck, mode)
                val board = deck.take(boardSize)

                _gameState.value = GameState(
                    gameId = data.roomId,
                    mode = mode,
                    deck = deck,
                    board = board,
                    selectedCards = emptySet(),
                    foundSets = foundSets,
                    usedCards = fullDeck.toSet() - deck.toSet(),
                    startTime = data.startedAt ?: System.currentTimeMillis(),
                    elapsedTime = ((System.currentTimeMillis()) - (data.startedAt ?: 0L)).coerceAtLeast(0L),
                    hintsUsed = 0,
                    gameStatus = if (isCompleted(board, deck, mode)) GameStatus.COMPLETED else if (data.status == "ingame") GameStatus.IN_PROGRESS else GameStatus.NOT_STARTED,
                    lastAction = null,
                    timestamp = System.currentTimeMillis()
                )
                // Manage timer based on current game status
                val currentState = _gameState.value
                if (currentState.gameStatus == GameStatus.IN_PROGRESS) {
                    val s = data.startedAt
                    if (s != null) startTimer(s) else stopTimer()
                } else {
                    stopTimer()
                    // Freeze/display final elapsed time
                    data.startedAt?.let { s ->
                        _gameState.value = currentState.copy(
                            elapsedTime = (System.currentTimeMillis() - s).coerceAtLeast(0L)
                        )
                    }
                // Persist multiplayer history (host writes once) when completed
                if (!historyPersisted && _gameState.value.gameStatus == GameStatus.COMPLETED && data.startedAt != null) {
                    val myUid = auth.currentUser.firstOrNull()?.uid
                    if (myUid != null && myUid == data.hostId) {
                        historyPersisted = true
                        launch {
                            persistMultiplayerCompletion(
                                data = data,
                                mode = mode,
                                fullDeck = fullDeck,
                                finalBoard = board
                            )
                        }
                    }
                }

                }
                // If completed and DB still says ingame, host should mark completed once
                if (!completionMarked && _gameState.value.gameStatus == GameStatus.COMPLETED && data.status != "completed") {
                    val myUid = auth.currentUser.firstOrNull()?.uid
                    if (myUid != null && myUid == data.hostId) {
                        completionMarked = true
                        launch { repo.markCompleted(data.roomId) }
                    }
                }


            }
        }

        // Lazy load names for users seen in events
        viewModelScope.launch {
            gameData.collect { data ->
                val ids = data?.events?.map { it.userId }?.toSet().orEmpty()
                ids.forEach { uid ->
                    if (!names.value.containsKey(uid)) {
                        launch {
                            profiles.observeProfile(uid).collect { p ->
                                names.value = names.value + (uid to (p?.displayName ?: uid))
                            }
                        }
                    }
                }
            }
        }
    }

    fun setRoom(id: String) { completionMarked = false; roomId.value = id }

    fun selectCard(index: Int) {
        val state = _gameState.value
        if (state.gameStatus != GameStatus.IN_PROGRESS) return
        val selected = state.selectedCards.toMutableSet()
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        val setSize = state.mode.setTypes.first().size
        if (selected.size == setSize) {
            val cards = selected.map { state.board[it] }
            val ok = when (state.mode.setTypes.first()) {
                SetType.NORMAL -> SetAlgorithms.checkSetNormal(cards, state.mode)
                SetType.ULTRA -> SetAlgorithms.checkSetUltra(cards, state.mode)
                SetType.FOUR_SET -> SetAlgorithms.checkSet4Set(cards, state.mode)
                SetType.GHOST -> SetAlgorithms.checkSetGhost(cards, state.mode)
            }
            if (ok) {
                // Submit move
                viewModelScope.launch {
                    val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
                    repo.submitSet(roomId.value ?: return@launch, uid, cards.map { it.encoding })
                }
                _gameResult.value = GameResult.SetFound(FoundSet(cards, state.mode.setTypes.first()))
                _gameState.value = state.copy(selectedCards = emptySet())
            } else {
                _gameResult.value = GameResult.InvalidSet(cards)
                _gameState.value = state.copy(selectedCards = emptySet())
            }
        } else {
            _gameState.value = state.copy(selectedCards = selected)
        }
    }

    fun clearGameResult() { _gameResult.value = null }

    private fun isCompleted(board: List<Card>, deck: List<Card>, mode: GameMode): Boolean {
        val setType = mode.setTypes.first()
        val hasSets = SetAlgorithms.findSets(board, setType, mode).isNotEmpty()
        // Completion when no sets and all remaining cards are already on board (no extra in deck)
        return !hasSets && board.size >= deck.size
    }

    private fun computeBoardSize(deck: List<Card>, mode: GameMode): Int {
        val setType = mode.setTypes.first()
        val minBoard = mode.boardSize
        var size = minOf(deck.size, minBoard)
        while (size < deck.size) {
            val board = deck.take(size)
            if (SetAlgorithms.findSets(board, setType, mode).isNotEmpty()) break
            size += 3 - (size % 3)
        }
        return size
    }

    // Copy of singleplayer helper, simplified for multiplayer usage
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
            val cardIndices = cardsToRemove.mapNotNull { card ->
                mutableDeck.indexOf(card).takeIf { it >= 0 }
            }.sortedDescending()
            for ((i, cardIndex) in cardIndices.withIndex()) {

                if (cardIndex >= cutoff) {
                    mutableDeck.removeAt(cardIndex)
                } else {
                    val remainingToRemove = cardIndices.size - i
                    if (cutoff + remainingToRemove <= mutableDeck.size) {
                        val replacementCards = mutableDeck.subList(cutoff, cutoff + remainingToRemove).toList()
                        for ((j, replacement) in replacementCards.withIndex()) {
                            val targetIndex = cardIndices[cardIndices.size - 1 - j]
                            if (targetIndex < mutableDeck.size) {
                                mutableDeck[targetIndex] = replacement
                            }
                        }
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
        val newBoardSize = computeBoardSize(mutableDeck, mode)
        return Pair(mutableDeck, newBoardSize)
    }
    private suspend fun persistMultiplayerCompletion(
        data: MultiplayerGameData,
        mode: GameMode,
        fullDeck: List<Card>,
        finalBoard: List<Card>
    ) {
        val modeType = when {
            data.mode.contains("ultra", ignoreCase = true) -> GameModeType.ULTRA
            else -> GameModeType.NORMAL
        }
        // Re-simulate acceptance to derive canonical accepted events and per-player counts
        var deck = fullDeck.toMutableList()
        val accepted = mutableListOf<MpEvent>()
        val eventsHistory = mutableListOf<GameEvent>()
        data.events.sortedBy { it.time }.forEach { e ->
            val cards = e.cards.map { Card(it) }
            val (newDeck, newBoardSize) = removeCardsAndUpdateBoard(
                cardsToRemove = cards,
                currentDeck = deck,
                currentBoardSize = deck.take(mode.boardSize).size.coerceAtLeast(mode.boardSize),
                mode = mode,
                usedCards = emptySet()
            )
            if (newDeck != deck) {
                deck = newDeck.toMutableList()
                accepted += e
                eventsHistory += GameEvent(
                    type = "set",
                    timestamp = e.time,
                    playerId = e.userId,
                    cardEncodings = e.cards,
                    boardSize = newBoardSize
                )
            }
        }
        val participantIds: List<String> = data.events.map { it.userId }.distinct()
        val counts: Map<String, Int> = accepted.groupingBy { it.userId }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 0
        val winners = counts.filterValues { it == maxCount }.keys.ifEmpty { participantIds }.toList()
        val setsHistory = accepted.map { e ->
            SetFoundEvent(
                playerId = e.userId,
                timestamp = e.time,
                cardEncodings = e.cards
            )
        }
        val playerStats = participantIds.map { pid ->
            PlayerGameStats(
                playerId = pid,
                setsFound = counts[pid] ?: 0,
                timeMs = _gameState.value.elapsedTime
            )
        }
        val record = GameRecord(
            gameId = data.roomId,
            creationTimestamp = data.startedAt ?: System.currentTimeMillis(),
            finishTimestamp = (data.startedAt ?: System.currentTimeMillis()) + _gameState.value.elapsedTime,
            hostPlayerId = data.hostId ?: "",
            totalPlayers = participantIds.size,
            gameMode = modeType,
            playerMode = PlayerMode.MULTIPLAYER,
            winners = winners,
            setsFoundHistory = setsHistory,
            playerStats = playerStats,
            seed = data.startedAt,
            initialDeckEncodings = fullDeck.map { it.encoding },
            events = eventsHistory,
            finalBoardEncodings = finalBoard.map { it.encoding }
        )
        runCatching {
            historyRepository.updateGameRecord(record)
        }.onFailure {
            // In case of transient error, allow another attempt by clearing the guard
            historyPersisted = false
        }
    }

}

