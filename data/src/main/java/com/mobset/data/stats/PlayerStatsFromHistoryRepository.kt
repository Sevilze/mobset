package com.mobset.data.stats

import com.mobset.data.history.AggregatedPlayerStats
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates stats from game history in client. Firestore indexes are used for pre-filtering.
 */
@Singleton
class PlayerStatsFromHistoryRepository @Inject constructor(
    private val history: GameHistoryRepository
) : PlayerStatsRepository {
    override fun observeAggregatedStats(
        playerId: String,
        gameMode: GameModeType?,
        playerMode: PlayerMode?
    ): Flow<AggregatedPlayerStats> = history.observeUserGames(playerId, gameMode, playerMode).map { games ->
        if (games.isEmpty()) return@map AggregatedPlayerStats(
            playerId = playerId,
            filterMode = gameMode,
            filterPlayerMode = playerMode,
            finishedGames = 0,
            totalSetsFound = 0,
            averageSetsPerGame = 0.0,
            fastestGameWonMs = null,
            averageGameLengthMs = null,
            rating = if (playerMode == PlayerMode.MULTIPLAYER) 1200 else null
        )
        val filtered = games
        val finishedGames = filtered.size
        var totalSets = 0
        var sumDurations = 0L
        var fastestWin: Long? = null
        filtered.forEach { rec ->
            val playerStats = rec.playerStats.find { it.playerId == playerId }
            if (playerStats != null) {
                totalSets += playerStats.setsFound
                val dur = (rec.finishTimestamp - rec.creationTimestamp).coerceAtLeast(0L)
                sumDurations += dur
                val isWinner = playerId in rec.winners
                if (isWinner) {
                    fastestWin = when (fastestWin) {
                        null -> dur
                        else -> minOf(fastestWin!!, dur)
                    }
                }
            }
        }
        val avgSets = if (finishedGames > 0) totalSets.toDouble() / finishedGames else 0.0
        val avgLen = if (finishedGames > 0) sumDurations / finishedGames else null
        val rating = if (playerMode == PlayerMode.MULTIPLAYER) 1200 else null // placeholder
        AggregatedPlayerStats(
            playerId = playerId,
            filterMode = gameMode,
            filterPlayerMode = playerMode,
            finishedGames = finishedGames,
            totalSetsFound = totalSets,
            averageSetsPerGame = avgSets,
            fastestGameWonMs = fastestWin,
            averageGameLengthMs = avgLen,
            rating = rating
        )
    }
}

