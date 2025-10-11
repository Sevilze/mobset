package com.mobset.domain.model

/**
 * Represents the current state of a Set game.
 */
data class GameState(
    val gameId: String = "",
    val mode: GameMode = GameMode.NORMAL,
    val deck: List<Card> = emptyList(),
    val board: List<Card> = emptyList(),
    val selectedCards: Set<Int> = emptySet(),
    val foundSets: List<FoundSet> = emptyList(),
    val usedCards: Set<Card> = emptySet(),
    val startTime: Long = System.currentTimeMillis(),
    val elapsedTime: Long = 0,
    val hintsUsed: Int = 0,
    val gameStatus: GameStatus = GameStatus.NOT_STARTED,
    val lastAction: GameAction? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a found set of cards.
 */
data class FoundSet(
    val cards: List<Card>,
    val type: SetType,
    val foundBy: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the current status of the game.
 */
enum class GameStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * Represents actions that can be performed in the game.
 */
sealed class GameAction {
    data class SelectCard(val cardIndex: Int) : GameAction()

    data class DeselectCard(val cardIndex: Int) : GameAction()

    object ClearSelection : GameAction()

    object CheckSet : GameAction()

    object DealCards : GameAction()

    object UseHint : GameAction()

    object ShuffleBoard : GameAction()

    object NewGame : GameAction()

    object PauseGame : GameAction()

    object ResumeGame : GameAction()
}

/**
 * Represents the result of a game action.
 */
sealed class GameResult {
    object Success : GameResult()

    data class SetFound(val foundSet: FoundSet) : GameResult()

    data class InvalidSet(val selectedCards: List<Card>) : GameResult()

    data class Error(val message: String) : GameResult()

    data class Hint(val cardIndices: List<Int>) : GameResult()

    object NoSetsAvailable : GameResult()

    object GameCompleted : GameResult()
}
