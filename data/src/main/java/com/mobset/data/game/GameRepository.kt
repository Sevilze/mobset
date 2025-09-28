package com.mobset.data.game

import kotlinx.coroutines.flow.Flow

data class GameSnapshot(
    val gameId: String,
    val mode: String,
    val boardEncodings: List<String>,
    val deckRemaining: Int,
    val lastUpdated: Long
)

interface GameRepository {
    fun observeGame(gameId: String): Flow<GameSnapshot?>
    suspend fun saveSnapshot(snapshot: GameSnapshot)
}

