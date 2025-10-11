package com.mobset.data.history

import kotlinx.coroutines.flow.Flow

interface GameHistoryRepository {
    fun observeUserGames(
        userId: String,
        gameMode: GameModeType? = null,
        playerMode: PlayerMode? = null
    ): Flow<List<GameRecord>>

    suspend fun addGameRecord(record: GameRecord)

    suspend fun updateGameRecord(record: GameRecord)
}
