package com.mobset.data.stats

import com.mobset.data.history.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.firstOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHistoryRepo : GameHistoryRepository {
    private val flow = MutableStateFlow<List<GameRecord>>(emptyList())
    override fun observeUserGames(userId: String, gameMode: GameModeType?, playerMode: PlayerMode?): Flow<List<GameRecord>> = flow.asStateFlow()
    override suspend fun addGameRecord(record: GameRecord) { flow.update { it + record } }
    override suspend fun updateGameRecord(record: GameRecord) { /*noop*/ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStatsFromHistoryRepositoryTest {

    @Test
    fun aggregates_basic_metrics() = runTest {
        val fake = FakeHistoryRepo()
        val repo = PlayerStatsFromHistoryRepository(fake)
        val uid = "u1"
        // Prepare two games: one win, one loss
        val g1 = GameRecord(
            gameId = "g1",
            creationTimestamp = 0L,
            finishTimestamp = 120_000L,
            hostPlayerId = uid,
            totalPlayers = 2,
            gameMode = GameModeType.NORMAL,
            playerMode = PlayerMode.MULTIPLAYER,
            winners = listOf(uid),
            setsFoundHistory = emptyList(),
            playerStats = listOf(
                PlayerGameStats(uid, setsFound = 5, timeMs = 120_000L),
                PlayerGameStats("u2", setsFound = 3, timeMs = 120_000L)
            )
        )
        val g2 = GameRecord(
            gameId = "g2",
            creationTimestamp = 0L,
            finishTimestamp = 180_000L,
            hostPlayerId = "u2",
            totalPlayers = 2,
            gameMode = GameModeType.NORMAL,
            playerMode = PlayerMode.MULTIPLAYER,
            winners = listOf("u2"),
            setsFoundHistory = emptyList(),
            playerStats = listOf(
                PlayerGameStats(uid, setsFound = 4, timeMs = 180_000L),
                PlayerGameStats("u2", setsFound = 6, timeMs = 180_000L)
            )
        )
        fake.addGameRecord(g1)
        fake.addGameRecord(g2)

        val stats = repo.observeAggregatedStats(uid, GameModeType.NORMAL, PlayerMode.MULTIPLAYER)
        val first = stats.firstOrNull()
        requireNotNull(first)
        assertEquals(2, first.finishedGames)
        assertEquals(9, first.totalSetsFound)
        assertEquals(120_000L, first.fastestGameWonMs)
        assertEquals(150_000L, first.averageGameLengthMs)
    }
}

