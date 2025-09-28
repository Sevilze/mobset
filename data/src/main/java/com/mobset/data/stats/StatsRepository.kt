package com.mobset.data.stats

import kotlinx.coroutines.flow.Flow

data class GameStats(
    val uid: String,
    val gamesPlayed: Int,
    val bestTimeMs: Long,
    val setsFound: Int
)

interface StatsRepository {
    fun observeStats(uid: String): Flow<GameStats?>
    suspend fun incrementGamesPlayed(uid: String)
    suspend fun recordBestTime(uid: String, timeMs: Long)
    suspend fun addSetsFound(uid: String, count: Int)
}

