package com.mobset.data.history

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreGameHistoryRepository @Inject constructor(
    private val db: FirebaseFirestore
) : GameHistoryRepository {

    private fun col() = db.collection("gameHistory")

    override fun observeUserGames(userId: String, gameMode: GameModeType?, playerMode: PlayerMode?): Flow<List<GameRecord>> = callbackFlow {
        var q: Query = col().whereArrayContains("participantIds", userId)
        if (gameMode != null) q = q.whereEqualTo("gameMode", gameMode.name.lowercase())
        if (playerMode != null) q = q.whereEqualTo("playerMode", playerMode.name.lowercase())
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { doc ->
                try {
                    val gameId = doc.getString("gameId") ?: doc.id
                    val creation = doc.getLong("creationTimestamp") ?: 0L
                    val finish = doc.getLong("finishTimestamp") ?: 0L
                    val host = doc.getString("hostPlayerId") ?: ""
                    val total = (doc.getLong("totalPlayers") ?: 1L).toInt()
                    val gMode = when ((doc.getString("gameMode") ?: "normal").lowercase()) {
                        "ultra" -> GameModeType.ULTRA
                        else -> GameModeType.NORMAL
                    }
                    val pMode = when ((doc.getString("playerMode") ?: "solo").lowercase()) {
                        "multiplayer" -> PlayerMode.MULTIPLAYER
                        else -> PlayerMode.SOLO
                    }
                    val winners = (doc.get("winners") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val setsHist = (doc.get("setsFoundHistory") as? List<Map<String, Any?>>)?.map { m ->
                        SetFoundEvent(
                            playerId = m["playerId"] as? String ?: "",
                            timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L,
                            cardEncodings = (m["cardEncodings"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    } ?: emptyList()
                    val playerStats = (doc.get("playerStats") as? List<Map<String, Any?>>)?.map { m ->
                        PlayerGameStats(
                            playerId = m["playerId"] as? String ?: "",
                            setsFound = (m["setsFound"] as? Number)?.toInt() ?: 0,
                            timeMs = (m["timeMs"] as? Number)?.toLong() ?: 0L
                        )
                    } ?: emptyList()
                    GameRecord(
                        gameId = gameId,
                        creationTimestamp = creation,
                        finishTimestamp = finish,
                        hostPlayerId = host,
                        totalPlayers = total,
                        gameMode = gMode,
                        playerMode = pMode,
                        winners = winners,
                        setsFoundHistory = setsHist,
                        playerStats = playerStats
                    )
                } catch (_: Throwable) { null }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun addGameRecord(record: GameRecord) {
        val map = toMap(record)
        col().document(record.gameId).set(map).await()
    }

    override suspend fun updateGameRecord(record: GameRecord) {
        val map = toMap(record)
        col().document(record.gameId).set(map).await()
    }

    private fun toMap(record: GameRecord): Map<String, Any?> = mapOf(
        "gameId" to record.gameId,
        "creationTimestamp" to record.creationTimestamp,
        "finishTimestamp" to record.finishTimestamp,
        "hostPlayerId" to record.hostPlayerId,
        "totalPlayers" to record.totalPlayers,
        "gameMode" to record.gameMode.name.lowercase(),
        "playerMode" to record.playerMode.name.lowercase(),
        "winners" to record.winners,
        // For querying user games efficiently, store participantIds
        "participantIds" to record.playerStats.map { it.playerId }.distinct(),
        "setsFoundHistory" to record.setsFoundHistory.map { e ->
            mapOf(
                "playerId" to e.playerId,
                "timestamp" to e.timestamp,
                "cardEncodings" to e.cardEncodings
            )
        },
        "playerStats" to record.playerStats.map { s ->
            mapOf(
                "playerId" to s.playerId,
                "setsFound" to s.setsFound,
                "timeMs" to s.timeMs
            )
        }
    )
}

