package com.mobset.data.multiplayer

import kotlinx.coroutines.flow.Flow

/**
 * Realtime multiplayer game data access via Firebase Realtime Database.
 */

data class MpEvent(
    val userId: String,
    val time: Long,
    val cards: List<String>
)

data class MultiplayerGameData(
    val roomId: String,
    val mode: String,
    val status: String,
    val startedAt: Long?,
    val hostId: String?,
    val events: List<MpEvent>
)

interface MultiplayerGameRepository {
    fun observeGame(roomId: String): Flow<MultiplayerGameData?>

    suspend fun submitSet(roomId: String, uid: String, cards: List<String>)

    suspend fun markCompleted(roomId: String)
}

