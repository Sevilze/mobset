package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.history.GameEvent
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.history.GameModeType
import com.mobset.data.history.GameRecord
import com.mobset.data.history.PlayerGameStats
import com.mobset.data.history.PlayerMode
import com.mobset.data.history.SetFoundEvent
import com.mobset.data.multiplayer.MpEvent
import com.mobset.data.multiplayer.MultiplayerGameData
import com.mobset.data.multiplayer.MultiplayerGameRepository
import com.mobset.data.presence.PresenceTracker
import com.mobset.data.profile.ProfileRepository
import com.mobset.data.rooms.RoomsRepository
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerGameViewModel
@Inject
constructor(
    private val auth: AuthRepository,
    private val repo: MultiplayerGameRepository,
    private val profiles: ProfileRepository,
    private val historyRepository: GameHistoryRepository,
    private val presence: PresenceTracker,
    private val rooms: RoomsRepository
) : ViewModel() {
    private val roomId = MutableStateFlow<String?>(null)

    private val gameData: StateFlow<MultiplayerGameData?> =
        roomId
            .filterNotNull()
            .flatMapLatest { id -> repo.observeGame(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult

    private var completionMarked = false
    private var historyPersisted = false

    fun acknowledgeCompletionStay() {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.setPostGameAck(id, uid, true)
            // If a new game already started by the time user acks, auto-kick them to avoid mismatch
            val s = rooms.observeRoom(id).firstOrNull()
            if (s?.status?.name?.equals("INGAME", true) == true) {
                rooms.leaveRoom(id, uid)
            }
        }
    }

    fun acknowledgeCompletionLeave(onLeft: (() -> Unit)? = null) {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.setPostGameAck(id, uid, true)
            rooms.leaveRoom(id, uid)
            onLeft?.invoke()
        }
    }

    private var timerJob: Job? = null

    private fun startTimer(startedAt: Long) {
        timerJob?.cancel()
        timerJob =
            viewModelScope.launch {
                while (isActive && _gameState.value.gameStatus == GameStatus.IN_PROGRESS) {
                    val now = System.currentTimeMillis()
                    val current = _gameState.value
                    _gameState.value =
                        current.copy(
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

    // uid->display name cache and raw found sets to allow reactive name mapping
    private val names = MutableStateFlow<Map<String, String>>(emptyMap())

    private data class RawFound(
        val userId: String,
        val cards: List<Card>,
        val timestamp: Long,
        val type: SetType
    )

    private var lastRawFoundSets: List<RawFound> = emptyList()

    private fun mapRawFoundToDisplay(
        raw: List<RawFound>,
        nameMap: Map<String, String>
    ): List<FoundSet> = raw.map { r ->
        FoundSet(
            cards = r.cards,
            type = r.type,
            foundBy = nameMap[r.userId],
            timestamp = r.timestamp
        )
    }

    init {
        presence.ensureTracking()

        // Rebuild local game state when remote data changes
        viewModelScope.launch {
            gameData.collect { data ->
                if (data == null) return@collect
                val mode = GameMode.fromId(data.mode) ?: GameMode.NORMAL
                // Wait for authoritative start time to avoid flickering different initial boards
                if (data.startedAt == null ||
                    (data.status != "INGAME" && data.status != "COMPLETED")
                ) {
                    stopTimer()
                    _gameState.value =
                        GameState(
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
                            gameStatus = if (data.status ==
                                "COMPLETED"
                            ) {
                                GameStatus.COMPLETED
                            } else {
                                GameStatus.NOT_STARTED
                            },
                            lastAction = null,
                            timestamp = System.currentTimeMillis()
                        )
                    if (data.status == "COMPLETED") {
                        val s = data.startedAt
                        val e = data.endedAt
                        if (s != null && e != null) {
                            _gameState.value =
                                _gameState.value.copy(elapsedTime = (e - s).coerceAtLeast(0L))
                        }
                    }
                    return@collect
                }

                val seed = data.startedAt ?: 0L
                val fullDeck = SetAlgorithms.generateDeck(mode, seed)
                var deck = fullDeck.toMutableList()

                // Process events in time order
                // Build raw found sets with userIds and map to display names reactively
                val rawFound = mutableListOf<RawFound>()
                data.events.sortedBy { it.time }.forEach { e ->
                    val cards = e.cards.map { Card(it) }
                    val (newDeck, newBoardSize) =
                        removeCardsAndUpdateBoard(
                            cardsToRemove = cards,
                            currentDeck = deck,
                            currentBoardSize = deck.take(
                                mode.boardSize
                            ).size.coerceAtLeast(mode.boardSize),
                            mode = mode,
                            usedCards = emptySet()
                        )
                    if (newDeck != deck) {
                        deck = newDeck.toMutableList()
                        rawFound +=
                            RawFound(
                                userId = e.userId,
                                cards = cards,
                                timestamp = e.time,
                                type = mode.setTypes.first()
                            )
                    }
                }
                lastRawFoundSets = rawFound
                val foundSets = mapRawFoundToDisplay(rawFound, names.value)

                val setType = mode.setTypes.first()
                val boardSize = computeBoardSize(deck, mode)
                val board = deck.take(boardSize)

                _gameState.value =
                    GameState(
                        gameId = data.roomId,
                        mode = mode,
                        deck = deck,
                        board = board,
                        selectedCards = emptySet(),
                        foundSets = foundSets,
                        usedCards = fullDeck.toSet() - deck.toSet(),
                        startTime = data.startedAt ?: System.currentTimeMillis(),
                        elapsedTime = ((System.currentTimeMillis()) - (data.startedAt ?: 0L))
                            .coerceAtLeast(0L),
                        hintsUsed = 0,
                        gameStatus =
                        if (isCompleted(board, deck, mode)) {
                            GameStatus.COMPLETED
                        } else if (data.status ==
                            "INGAME"
                        ) {
                            GameStatus.IN_PROGRESS
                        } else {
                            GameStatus.NOT_STARTED
                        },
                        lastAction = null,
                        timestamp = System.currentTimeMillis()
                    )

                // If names update later, remap foundBy values reactively
                launch {
                    names.collect { nm ->
                        if (lastRawFoundSets.isNotEmpty()) {
                            val remapped = mapRawFoundToDisplay(lastRawFoundSets, nm)
                            _gameState.value = _gameState.value.copy(foundSets = remapped)
                        }
                    }
                }

                // Manage timer based on current game status
                val currentState = _gameState.value
                if (currentState.gameStatus == GameStatus.IN_PROGRESS) {
                    val s = data.startedAt
                    if (s != null) startTimer(s) else stopTimer()
                } else {
                    stopTimer()
                    val s = data.startedAt
                    val e = data.endedAt
                    if (s != null && e != null) {
                        _gameState.value =
                            currentState.copy(
                                elapsedTime = (e - s).coerceAtLeast(0L)
                            )
                    } else {
                        s?.let { start ->
                            _gameState.value =
                                currentState.copy(
                                    elapsedTime = (System.currentTimeMillis() - start)
                                        .coerceAtLeast(0L)
                                )
                        }
                    }
                    // Persist multiplayer history (host writes once) when COMPLETED
                    if (!historyPersisted && _gameState.value.gameStatus == GameStatus.COMPLETED &&
                        data.startedAt != null
                    ) {
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
                // If COMPLETED and DB still says INGAME, host should mark COMPLETED once
                if (!completionMarked && _gameState.value.gameStatus == GameStatus.COMPLETED &&
                    data.status != "COMPLETED"
                ) {
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
                val ids =
                    data
                        ?.events
                        ?.map { it.userId }
                        ?.toSet()
                        .orEmpty()
                ids.forEach { uid ->
                    if (!names.value.containsKey(uid)) {
                        launch {
                            profiles.observeProfile(uid).collect { p ->
                                names.value =
                                    names.value + (uid to (p?.displayName ?: "Unknown"))
                            }
                        }
                    }
                }
            }
        }
        // Also prefetch host display name to avoid showing IDs in any UI tied to host
        viewModelScope.launch {
            gameData.collect { data ->
                val uid = data?.hostId ?: return@collect
                if (!names.value.containsKey(uid)) {
                    launch {
                        profiles.observeProfile(uid).collect { p ->
                            names.value = names.value + (uid to (p?.displayName ?: "Unknown"))
                        }
                    }
                }
            }
        }
    }

    fun setRoom(id: String) {
        completionMarked = false
        roomId.value = id
    }

    fun selectCard(index: Int) {
        val state = _gameState.value
        if (state.gameStatus != GameStatus.IN_PROGRESS) return
        val selected = state.selectedCards.toMutableSet()
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        val setSize =
            state.mode.setTypes
                .first()
                .size
        if (selected.size == setSize) {
            val cards = selected.map { state.board[it] }
            val ok =
                when (state.mode.setTypes.first()) {
                    SetType.NORMAL -> SetAlgorithms.checkSetNormal(cards, state.mode)
                    SetType.ULTRA -> SetAlgorithms.checkSetUltra(cards, state.mode)
                    SetType.FOUR_SET -> SetAlgorithms.checkSet4Set(cards, state.mode)
                    SetType.GHOST -> SetAlgorithms.checkSetGhost(cards, state.mode)
                }
            if (ok) {
                // Submit move
                viewModelScope.launch {
                    val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
                    repo.submitSet(
                        roomId.value ?: return@launch,
                        uid,
                        cards.map {
                            it.encoding
                        }
                    )
                }
                _gameResult.value =
                    GameResult.SetFound(FoundSet(cards, state.mode.setTypes.first()))
                _gameState.value = state.copy(selectedCards = emptySet())
            } else {
                _gameResult.value = GameResult.InvalidSet(cards)
                _gameState.value = state.copy(selectedCards = emptySet())
            }
        } else {
            _gameState.value = state.copy(selectedCards = selected)
        }
    }

    fun clearGameResult() {
        _gameResult.value = null
    }

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
            val cardIndices =
                cardsToRemove
                    .mapNotNull { card ->
                        mutableDeck.indexOf(card).takeIf { it >= 0 }
                    }.sortedDescending()
            for ((i, cardIndex) in cardIndices.withIndex()) {
                if (cardIndex >= cutoff) {
                    mutableDeck.removeAt(cardIndex)
                } else {
                    val remainingToRemove = cardIndices.size - i
                    if (cutoff + remainingToRemove <= mutableDeck.size) {
                        val replacementCards = mutableDeck.subList(
                            cutoff,
                            cutoff + remainingToRemove
                        ).toList()
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
        val modeType =
            when {
                data.mode.contains("ultra", ignoreCase = true) -> GameModeType.ULTRA
                else -> GameModeType.NORMAL
            }
        // Re-simulate acceptance to derive canonical accepted events and per-player counts
        var deck = fullDeck.toMutableList()
        val accepted = mutableListOf<MpEvent>()
        val eventsHistory = mutableListOf<GameEvent>()
        data.events.sortedBy { it.time }.forEach { e ->
            val cards = e.cards.map { Card(it) }
            val (newDeck, newBoardSize) =
                removeCardsAndUpdateBoard(
                    cardsToRemove = cards,
                    currentDeck = deck,
                    currentBoardSize = deck.take(
                        mode.boardSize
                    ).size.coerceAtLeast(mode.boardSize),
                    mode = mode,
                    usedCards = emptySet()
                )
            if (newDeck != deck) {
                deck = newDeck.toMutableList()
                accepted += e
                eventsHistory +=
                    GameEvent(
                        type = "set",
                        timestamp = e.time,
                        playerId = e.userId,
                        cardEncodings = e.cards,
                        boardSize = newBoardSize
                    )
            }
        }

        val participantsFromRoom: List<String> = runCatching {
            repo.getParticipants(data.roomId)
        }.getOrDefault(emptyList())
        val participantIds: List<String> = participantsFromRoom.distinct()
        val counts: Map<String, Int> = accepted.groupingBy { it.userId }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 0
        val winners =
            counts
                .filterValues { it == maxCount }
                .keys
                .ifEmpty { participantIds }
                .toList()
        val setsHistory =
            accepted.map { e ->
                SetFoundEvent(
                    playerId = e.userId,
                    timestamp = e.time,
                    cardEncodings = e.cards
                )
            }
        val playerStats =
            participantIds.map { pid ->
                PlayerGameStats(
                    playerId = pid,
                    setsFound = counts[pid] ?: 0,
                    timeMs = _gameState.value.elapsedTime
                )
            }
        val record =
            GameRecord(
                gameId = data.gameId,
                creationTimestamp = data.startedAt ?: System.currentTimeMillis(),
                finishTimestamp =
                (data.startedAt ?: System.currentTimeMillis()) +
                    _gameState.value.elapsedTime,
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
