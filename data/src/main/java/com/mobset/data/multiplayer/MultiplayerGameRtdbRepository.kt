package com.mobset.data.multiplayer

import com.google.firebase.database.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class MultiplayerGameRtdbRepository
@Inject
constructor(private val rtdb: FirebaseDatabase) :
    MultiplayerGameRepository {
    private fun roomsRef(): DatabaseReference = rtdb.getReference("rooms")

    private fun gamesRef(): DatabaseReference = rtdb.getReference("games")

    private fun gameDataRef(): DatabaseReference = rtdb.getReference("gameData")

    override fun observeGame(roomId: String): Flow<MultiplayerGameData?> = callbackFlow {
        val roomRef = roomsRef().child(roomId)

        var currentGameId: String? = null
        var latestMode: String? = null
        var latestStatus: String? = null
        var latestStartedAt: Long? = null
        var latestEndedAt: Long? = null
        var latestHost: String? = null
        var latestEvents: List<MpEvent> = emptyList()

        var headerListener: ValueEventListener? = null
        var eventsListener: ValueEventListener? = null

        fun attachGameListeners(gameId: String) {
            // Detach previous
            headerListener?.let { gamesRef().child(gameId).removeEventListener(it) }
            eventsListener?.let {
                gameDataRef().child(gameId).child("events").removeEventListener(it)
            }

            headerListener =
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            trySend(null)
                            return
                        }
                        latestMode = snapshot.child("mode").getValue(String::class.java)
                        latestStatus = snapshot.child("status").getValue(String::class.java)
                        latestStartedAt =
                            snapshot.child("startedAt").getValue(Long::class.java)
                        latestEndedAt = snapshot.child("endedAt").getValue(Long::class.java)
                        latestHost = snapshot.child("host").getValue(String::class.java)
                        val gid = currentGameId ?: return
                        val mode = latestMode ?: return
                        val status = latestStatus ?: return
                        trySend(
                            MultiplayerGameData(
                                roomId = roomId,
                                gameId = gid,
                                mode = mode,
                                status = status,
                                startedAt = latestStartedAt,
                                endedAt = latestEndedAt,
                                hostId = latestHost,
                                events = latestEvents
                            )
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        trySend(null)
                    }
                }
            gamesRef().child(gameId).addValueEventListener(headerListener!!)

            eventsListener =
                object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val list = mutableListOf<MpEvent>()
                        for (c in s.children) {
                            val user =
                                c.child("user").getValue(String::class.java) ?: continue
                            val time =
                                c.child("time").getValue(Long::class.java) ?: continue
                            val cards =
                                buildList<String> {
                                    c.child("c1").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                    c.child("c2").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                    c.child("c3").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                    c.child("c4").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                    c.child("c5").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                    c.child("c6").getValue(String::class.java)?.let {
                                        add(it)
                                    }
                                }
                            if (cards.isNotEmpty()) list.add(MpEvent(user, time, cards))
                        }
                        latestEvents = list.sortedBy { it.time }
                        val gid = currentGameId ?: return
                        val mode = latestMode ?: return
                        val status = latestStatus ?: return
                        trySend(
                            MultiplayerGameData(
                                roomId = roomId,
                                gameId = gid,
                                mode = mode,
                                status = status,
                                startedAt = latestStartedAt,
                                endedAt = latestEndedAt,
                                hostId = latestHost,
                                events = latestEvents
                            )
                        )
                    }

                    override fun onCancelled(error: DatabaseError) { /* keep last */ }
                }
            gameDataRef().child(
                gameId
            ).child("events").addValueEventListener(eventsListener!!)
        }

        val roomListener =
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val gid = snapshot.child("currentGameId").getValue(String::class.java)
                    if (gid == null) {
                        trySend(null)
                        return
                    }
                    if (gid != currentGameId) {
                        currentGameId = gid
                        attachGameListeners(gid)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }

        roomRef.addValueEventListener(roomListener)
        awaitClose {
            roomRef.removeEventListener(roomListener)
            currentGameId?.let { gid ->
                headerListener?.let { gamesRef().child(gid).removeEventListener(it) }
                eventsListener?.let {
                    gameDataRef().child(gid).child("events").removeEventListener(it)
                }
            }
        }
    }

    override suspend fun submitSet(roomId: String, uid: String, cards: List<String>) {
        require(cards.size in 3..6) { "cards must contain 3 to 6 encodings" }
        val gid =
            roomsRef()
                .child(roomId)
                .child("currentGameId")
                .get()
                .await()
                .getValue(String::class.java)
                ?: return
        val payload =
            hashMapOf<String, Any>(
                "user" to uid,
                "time" to ServerValue.TIMESTAMP
            )
        val fields = listOf("c1", "c2", "c3", "c4", "c5", "c6")
        cards.forEachIndexed { i, enc ->
            require(enc.matches(Regex("^[0-3]{3,5}$"))) { "invalid card encoding: $enc" }
            payload[fields[i]] = enc
        }
        gameDataRef()
            .child(gid)
            .child("events")
            .push()
            .setValue(payload)
            .await()
    }

    override suspend fun markCompleted(roomId: String) {
        val room = roomsRef().child(roomId).get().await()
        val gid = room.child("currentGameId").getValue(String::class.java) ?: return
        val access = room.child("access").getValue(String::class.java) ?: "public"
        val users = room.child("users").children.mapNotNull { it.key }
        val updates = hashMapOf<String, Any?>()
        updates["/games/$gid/status"] = "COMPLETED"
        updates["/games/$gid/endedAt"] = ServerValue.TIMESTAMP
        updates["/rooms/$roomId/status"] = "WAITING"
        updates["/rooms/$roomId/currentGameId"] = null
        // Initialize post-game acknowledgements to false for all current members
        users.forEach { u -> updates["/rooms/$roomId/postGameAck/$u"] = false }
        // Record ack initialization time for 20s timeout window
        updates["/rooms/$roomId/postGameAckMeta/startedAt"] = ServerValue.TIMESTAMP
        if (access == "public") {
            updates["/publicRooms/$roomId"] = ServerValue.TIMESTAMP
        }
        rtdb.reference.updateChildren(updates).await()
    }

    override suspend fun getParticipants(roomId: String): List<String> {
        val gid =
            roomsRef()
                .child(roomId)
                .child("currentGameId")
                .get()
                .await()
                .getValue(String::class.java)
                ?: return emptyList()
        val p =
            gamesRef()
                .child(gid)
                .child("participants")
                .get()
                .await()
        return p.children.mapNotNull { it.key }
    }
}
