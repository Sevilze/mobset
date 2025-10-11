package com.mobset.data.stats

import com.mobset.data.history.AggregatedPlayerStats
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import kotlinx.coroutines.flow.Flow

interface PlayerStatsRepository {
    fun observeAggregatedStats(
        playerId: String,
        gameMode: GameModeType? = null,
        playerMode: PlayerMode? = null
    ): Flow<AggregatedPlayerStats>
}
