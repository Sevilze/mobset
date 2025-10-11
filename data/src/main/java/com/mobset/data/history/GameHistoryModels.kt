package com.mobset.data.history

enum class PlayerMode { SOLO, MULTIPLAYER }

enum class GameModeType { NORMAL, ULTRA }

data class SetFoundEvent(val playerId: String, val timestamp: Long, val cardEncodings: List<String>)

/**
 * Normalized event record to reconstruct full game state.
 */
data class GameEvent(
    val type: String,
    val timestamp: Long,
    val playerId: String? = null,
    val cardEncodings: List<String>? = null,
    val boardSize: Int? = null
)

data class PlayerGameStats(val playerId: String, val setsFound: Int, val timeMs: Long)

data class GameRecord(
    val gameId: String,
    val creationTimestamp: Long,
    val finishTimestamp: Long,
    val hostPlayerId: String,
    val totalPlayers: Int,
    val gameMode: GameModeType,
    val playerMode: PlayerMode,
    val winners: List<String>,
    val setsFoundHistory: List<SetFoundEvent>,
    val playerStats: List<PlayerGameStats>,
    val seed: Long? = null,
    val initialDeckEncodings: List<String> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val finalBoardEncodings: List<String> = emptyList()
)

/**
 * Aggregated player statistics with optional filters.
 */
data class AggregatedPlayerStats(
    val playerId: String,
    val filterMode: GameModeType? = null,
    val filterPlayerMode: PlayerMode? = null,
    val finishedGames: Int,
    val totalSetsFound: Int,
    val averageSetsPerGame: Double,
    val fastestGameWonMs: Long?,
    val averageGameLengthMs: Long?,
    // ELO (only meaningful for multiplayer); null or N/A if solo-only
    val rating: Int?
)
