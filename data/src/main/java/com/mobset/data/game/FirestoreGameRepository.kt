package com.mobset.data.game

import com.google.firebase.firestore.FirebaseFirestore
// Using encoding strings in data layer to avoid UI module dependency
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreGameRepository @Inject constructor(
    private val db: FirebaseFirestore
) : GameRepository {

    private fun col() = db.collection("games")

    override fun observeGame(gameId: String): Flow<GameSnapshot?> = callbackFlow {
        val reg = col().document(gameId).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            val data = snap?.data ?: run { trySend(null); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST")
            val board = (data["board"] as? List<String>) ?: emptyList()
            trySend(
                GameSnapshot(
                    gameId = gameId,
                    mode = data["mode"] as? String ?: "normal",
                    boardEncodings = board,
                    deckRemaining = (data["deckRemaining"] as? Number)?.toInt() ?: 0,
                    lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: 0L
                )
            )
        }
        awaitClose { reg.remove() }
    }

    override suspend fun saveSnapshot(snapshot: GameSnapshot) {
        val map = mapOf(
            "mode" to snapshot.mode,
            "board" to snapshot.boardEncodings,
            "deckRemaining" to snapshot.deckRemaining,
            "lastUpdated" to snapshot.lastUpdated
        )
        col().document(snapshot.gameId).set(map).await()
    }
}

