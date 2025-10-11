package com.mobset.data.stats

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreStatsRepository
@Inject
constructor(private val db: FirebaseFirestore) :
    StatsRepository {
    private fun doc(uid: String) = db.collection("stats").document(uid)

    override fun observeStats(uid: String): Flow<GameStats?> = callbackFlow {
        val reg =
            doc(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val data =
                    snap?.data ?: run {
                        trySend(null)
                        return@addSnapshotListener
                    }
                trySend(
                    GameStats(
                        uid = uid,
                        gamesPlayed = (data["gamesPlayed"] as? Number)?.toInt() ?: 0,
                        bestTimeMs = (data["bestTimeMs"] as? Number)?.toLong() ?: 0L,
                        setsFound = (data["setsFound"] as? Number)?.toInt() ?: 0
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    override suspend fun incrementGamesPlayed(uid: String) {
        doc(uid)
            .set(
                mapOf("gamesPlayed" to FieldValue.increment(1)),
                com.google.firebase.firestore.SetOptions
                    .merge()
            ).await()
    }

    override suspend fun recordBestTime(uid: String, timeMs: Long) {
        doc(uid)
            .set(
                mapOf("bestTimeMs" to timeMs),
                com.google.firebase.firestore.SetOptions
                    .merge()
            ).await()
    }

    override suspend fun addSetsFound(uid: String, count: Int) {
        doc(uid)
            .set(
                mapOf("setsFound" to FieldValue.increment(count.toLong())),
                com.google.firebase.firestore.SetOptions
                    .merge()
            ).await()
    }
}
