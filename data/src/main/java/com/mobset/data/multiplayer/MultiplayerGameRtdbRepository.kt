package com.mobset.data.multiplayer

import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiplayerGameRtdbRepository @Inject constructor(
    private val rtdb: FirebaseDatabase
) : MultiplayerGameRepository {

    private fun gamesRef(): DatabaseReference = rtdb.getReference("games")
    private fun gameDataRef(): DatabaseReference = rtdb.getReference("gameData")

    override fun observeGame(roomId: String): Flow<MultiplayerGameData?> = callbackFlow {
        val gameRef = gamesRef().child(roomId)
        val eventsRef = gameDataRef().child(roomId).child("events")

        var latestMode: String? = null
        var latestStatus: String? = null
        var latestStartedAt: Long? = null
        var latestHost: String? = null
        var latestEvents: List<MpEvent> = emptyList()
        var headerReady = false
        var eventsReady = false

        fun emitIfReady() {
            val mode = latestMode ?: return
            val status = latestStatus ?: return
            if (status == "ingame" && latestStartedAt == null) return
            if (!headerReady || !eventsReady) return
            trySend(
                MultiplayerGameData(
                    roomId = roomId,
                    mode = mode,
                    status = status,
                    startedAt = latestStartedAt,
                    hostId = latestHost,
                    events = latestEvents
                )
            )
        }

        val gameListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { trySend(null); return }
                latestMode = snapshot.child("mode").getValue(String::class.java)
                latestStatus = snapshot.child("status").getValue(String::class.java)
                latestStartedAt = snapshot.child("startedAt").getValue(Long::class.java)
                latestHost = snapshot.child("host").getValue(String::class.java)
                headerReady = true
                emitIfReady()
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        val eventsListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val list = mutableListOf<MpEvent>()
                for (c in s.children) {
                    val user = c.child("user").getValue(String::class.java) ?: continue
                    val time = c.child("time").getValue(Long::class.java) ?: continue
                    val cards = buildList<String> {
                        c.child("c1").getValue(String::class.java)?.let { add(it) }
                        c.child("c2").getValue(String::class.java)?.let { add(it) }
                        c.child("c3").getValue(String::class.java)?.let { add(it) }
                        c.child("c4").getValue(String::class.java)?.let { add(it) }
                        c.child("c5").getValue(String::class.java)?.let { add(it) }
                        c.child("c6").getValue(String::class.java)?.let { add(it) }
                    }
                    if (cards.isNotEmpty()) list.add(MpEvent(user, time, cards))
                }
                latestEvents = list.sortedBy { it.time }
                eventsReady = true
                emitIfReady()
            }
            override fun onCancelled(error: DatabaseError) { /* keep last */ }
        }

        gameRef.addValueEventListener(gameListener)
        eventsRef.addValueEventListener(eventsListener)
        awaitClose {
            gameRef.removeEventListener(gameListener)
            eventsRef.removeEventListener(eventsListener)
        }
    }

    override suspend fun submitSet(roomId: String, uid: String, cards: List<String>) {
        require(cards.size in 3..6) { "cards must contain 3 to 6 encodings" }
        val payload = hashMapOf<String, Any>(
            "user" to uid,
            "time" to ServerValue.TIMESTAMP,
        )
        val fields = listOf("c1", "c2", "c3", "c4", "c5", "c6")
        cards.forEachIndexed { i, enc ->
            require(enc.matches(Regex("^[0-3]{3,5}$"))) { "invalid card encoding: $enc" }
            payload[fields[i]] = enc
        }
        gameDataRef().child(roomId).child("events").push().setValue(payload).await()
    }

    override suspend fun markCompleted(roomId: String) {
        gamesRef().child(roomId).child("status").setValue("completed").await()
    }
}


