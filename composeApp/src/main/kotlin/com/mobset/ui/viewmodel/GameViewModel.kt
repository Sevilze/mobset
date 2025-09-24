package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Set game state and logic.
 */
class GameViewModel : ViewModel() {
    
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult.asStateFlow()
    
    /**
     * Starts a new game with the specified mode.
     */
    fun startNewGame(mode: GameMode) {
        viewModelScope.launch {
            val deck = SetAlgorithms.generateDeck(mode)
            val setType = mode.setTypes.first()
            val board = SetAlgorithms.findBoard(deck, mode, setType)
            
            _gameState.value = GameState(
                gameId = generateGameId(),
                mode = mode,
                deck = deck,
                board = board,
                selectedCards = emptySet(),
                foundSets = emptyList(),
                score = 0,
                hintsUsed = 0,
                gameStatus = GameStatus.IN_PROGRESS,
                lastAction = GameAction.NewGame,
                timestamp = System.currentTimeMillis()
            )
            
            _gameResult.value = null
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
            val newScore = currentState.score + calculateSetScore(setType)
            
            // Remove found cards from board and deal new ones if needed
            val newBoard = dealNewCards(currentState.board, selectedIndices, currentState.deck, currentState.mode)
            
            _gameState.value = currentState.copy(
                board = newBoard,
                selectedCards = emptySet(),
                foundSets = newFoundSets,
                score = newScore,
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
        
        val newBoard = dealNewCards(currentState.board, emptyList(), currentState.deck, currentState.mode)
        
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
    
    private fun dealNewCards(
        currentBoard: List<Card>,
        indicesToRemove: List<Int>,
        deck: List<Card>,
        mode: GameMode
    ): List<Card> {
        val mutableBoard = currentBoard.toMutableList()
        
        // Remove found cards (in reverse order to maintain indices)
        indicesToRemove.sortedDescending().forEach { index ->
            if (index < mutableBoard.size) {
                mutableBoard.removeAt(index)
            }
        }
        
        // Add new cards from deck if available
        val usedCards = mutableBoard.toSet()
        val availableCards = deck.filter { it !in usedCards }
        val cardsToAdd = minOf(indicesToRemove.size, availableCards.size)
        
        repeat(cardsToAdd) {
            if (availableCards.isNotEmpty()) {
                mutableBoard.add(availableCards[it])
            }
        }
        
        return mutableBoard
    }
    
    private fun calculateSetScore(setType: SetType): Int {
        return when (setType) {
            SetType.NORMAL -> 10
            SetType.ULTRA -> 15
            SetType.FOUR_SET -> 20
            SetType.GHOST -> 25
        }
    }
    
    private fun checkGameCompletion() {
        val currentState = _gameState.value
        val setType = currentState.mode.setTypes.first()
        val availableSets = SetAlgorithms.findSets(currentState.board, setType, currentState.mode)
        
        if (availableSets.isEmpty() && currentState.board.size < currentState.mode.boardSize) {
            _gameState.value = currentState.copy(gameStatus = GameStatus.COMPLETED)
            _gameResult.value = GameResult.GameCompleted
        }
    }
    
    private fun generateGameId(): String {
        return "game_${System.currentTimeMillis()}"
    }
}
