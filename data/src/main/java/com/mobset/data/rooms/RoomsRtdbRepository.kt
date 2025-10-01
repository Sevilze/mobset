package com.mobset.data.rooms

import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import java.util.*

@Singleton
class RoomsRtdbRepository @Inject constructor(
    private val rtdb: FirebaseDatabase
) : RoomsRepository {

    private fun gamesRef(): DatabaseReference = rtdb.getReference("games")
    private fun publicRef(): DatabaseReference = rtdb.getReference("publicGames")
    private fun userGamesRef(uid: String): DatabaseReference = rtdb.getReference("userGames").child(uid)
    private fun usersRef(uid: String): DatabaseReference = rtdb.getReference("users").child(uid)

    override fun observePublicRooms(limit: Int): Flow<List<RoomSummary>> = callbackFlow {
        val query = publicRef().orderByValue().limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RoomSummary>()
                // reverse chronological by createdAt (value)
                val children = snapshot.children.toList().sortedBy { it.getValue(Long::class.java) ?: 0L }
                for (child in children) {
                    val gameId = child.key ?: continue
                    val gameSnap = gamesRef().child(gameId)
                    gameSnap.get().addOnSuccessListener { g ->
                        val host = g.child("host").getValue(String::class.java) ?: return@addOnSuccessListener
                        val accessRaw = g.child("access").getValue(String::class.java) ?: "public"
                        val access = if (accessRaw == "password") Access.PASSWORD else Access.PUBLIC
                        val mode = g.child("mode").getValue(String::class.java) ?: "normal"
                        val createdAt = g.child("createdAt").getValue(Long::class.java) ?: 0L
                        val users = g.child("users").childrenCount.toInt()
                        val summary = RoomSummary(
                            id = gameId,
                            hostId = host,
                            access = access,
                            mode = mode,
                            createdAt = createdAt,
                            playerCount = users,
                        )
                        // Collect asynchronously andre-emit a consolidated list.
                        list.add(summary)
                        trySend(list.sortedByDescending { it.createdAt })
                    }
                }
                if (children.isEmpty()) trySend(emptyList())
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    override fun observeRoom(roomId: String): Flow<RoomState?> = callbackFlow {
        val ref = gamesRef().child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (!s.exists()) { trySend(null); return }
                val host = s.child("host").getValue(String::class.java) ?: return
                val accessRaw = s.child("access").getValue(String::class.java) ?: "public"
                val access = if (accessRaw == "password") Access.PASSWORD else Access.PUBLIC
                val mode = s.child("mode").getValue(String::class.java) ?: "normal"
                val statusRaw = s.child("status").getValue(String::class.java) ?: "waiting"
                val createdAt = s.child("createdAt").getValue(Long::class.java) ?: 0L
                val startedAt = s.child("startedAt").getValue(Long::class.java)
                val users = mutableMapOf<String, Long>()
                s.child("users").children.forEach { u ->
                    val uid = u.key ?: return@forEach
                    val t = u.getValue(Long::class.java) ?: 0L
                    users[uid] = t
                }
                val state = RoomState(
                    id = roomId,
                    hostId = host,
                    access = access,
                    mode = mode,
                    status = Status.valueOf(statusRaw),
                    createdAt = createdAt,
                    startedAt = startedAt,
                    users = users
                )
                trySend(state)
            }
            override fun onCancelled(error: DatabaseError) { trySend(null) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun createRoom(
        roomId: String,
        hostId: String,
        access: Access,
        mode: String,
        passwordPlain: String?,
        enableHint: Boolean
    ) {
        val now = ServerValue.TIMESTAMP
        val created = mutableMapOf<String, Any?>().apply {
            put("host", hostId)
            put("access", if (access == Access.PASSWORD) "password" else "public")
            put("mode", mode)
            put("status", "waiting")
            put("createdAt", now)
            put("enableHint", enableHint)
            if (access == Access.PASSWORD && !passwordPlain.isNullOrBlank()) {
                val salt = UUID.randomUUID().toString().replace("-", "")
                put("pwdSalt", salt)
                put("pwdHash", sha256(salt + ":" + passwordPlain))
            }
            // Users map starts empty; host will auto-join on first open
        }
        val updates = hashMapOf<String, Any?>()
        updates["/games/$roomId"] = created
        if (access == Access.PUBLIC) {
            updates["/publicGames/$roomId"] = now
        }
        updates["/userGames/$hostId/$roomId"] = now
        rtdb.reference.updateChildren(updates).await()
    }

    override suspend fun joinRoom(roomId: String, uid: String, passwordPlain: String?) {
        val game = gamesRef().child(roomId).get().await()
        if (!game.exists()) throw IllegalStateException("Room not found")
        val access = game.child("access").getValue(String::class.java) ?: "public"
        if (access == "password") {
            val salt = game.child("pwdSalt").getValue(String::class.java) ?: ""
            val expected = game.child("pwdHash").getValue(String::class.java) ?: ""
            val actual = sha256(salt + ":" + (passwordPlain ?: ""))
            require(actual == expected) { "Invalid room password" }
        }
        val updates = hashMapOf<String, Any?>()
        updates["/games/$roomId/users/$uid"] = ServerValue.TIMESTAMP
        updates["/userGames/$uid/$roomId"] = game.child("createdAt").value ?: ServerValue.TIMESTAMP
        rtdb.reference.updateChildren(updates).await()
        // Presence: mark current screen connection to this room.
        val conRef = usersRef(uid).child("connections").push()
        conRef.onDisconnect().removeValue()
        conRef.setValue("/room/$roomId")
        usersRef(uid).child("lastOnline").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    override suspend fun leaveRoom(roomId: String, uid: String) {
        val updates = hashMapOf<String, Any?>()
        updates["/games/$roomId/users/$uid"] = null
        updates["/userGames/$uid/$roomId"] = null
        rtdb.reference.updateChildren(updates).await()
    }

    override suspend fun startGame(roomId: String, hostId: String) {
        val ref = gamesRef().child(roomId)
        val game = ref.get().await()
        val host = game.child("host").getValue(String::class.java)
        require(host == hostId) { "Only host can start" }
        val updates = mapOf(
            "status" to "ingame",
            "startedAt" to ServerValue.TIMESTAMP
        )
        ref.updateChildren(updates).await()
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = md.digest(text.toByteArray())
        return b.joinToString("") { String.format("%02x", it) }
    }
}
