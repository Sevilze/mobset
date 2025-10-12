package com.mobset.data.rooms

import com.google.firebase.database.*
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class RoomsRtdbRepository
@Inject
constructor(private val rtdb: FirebaseDatabase) :
    RoomsRepository {
    private val allowedModes =
        setOf(
            "normal",
            "ultraset"
        )

    private fun roomsRef(): DatabaseReference = rtdb.getReference("rooms")

    private fun publicRoomsRef(): DatabaseReference = rtdb.getReference("publicRooms")

    private fun userRoomsRef(uid: String): DatabaseReference =
        rtdb.getReference("userRooms").child(uid)

    private fun usersRef(uid: String): DatabaseReference = rtdb.getReference("users").child(uid)

    private fun whitelistRef(roomId: String): DatabaseReference =
        roomsRef().child(roomId).child("whitelist")

    override fun observePublicRooms(limit: Int): Flow<List<RoomSummary>> = callbackFlow {
        val indexQuery = publicRoomsRef().orderByValue().limitToLast(limit)
        val roomListeners = mutableMapOf<String, ValueEventListener>()
        val summaries = mutableMapOf<String, RoomSummary>()

        fun attachRoomListener(roomId: String) {
            if (roomListeners.containsKey(roomId)) return
            val ref = roomsRef().child(roomId)
            val l =
                object : ValueEventListener {
                    override fun onDataChange(r: DataSnapshot) {
                        if (!r.exists()) {
                            summaries.remove(roomId)
                            trySend(summaries.values.sortedByDescending { it.createdAt })
                            return
                        }
                        val host = r.child("host").getValue(String::class.java) ?: return
                        val accessRaw =
                            r.child("access").getValue(String::class.java) ?: "public"
                        val access = if (accessRaw ==
                            "password"
                        ) {
                            Access.PASSWORD
                        } else {
                            Access.PUBLIC
                        }
                        val mode = r.child("mode").getValue(String::class.java) ?: "normal"
                        val createdAt =
                            r.child("createdAt").getValue(Long::class.java) ?: 0L
                        val name = r.child("name").getValue(String::class.java) ?: roomId
                        val users = r.child("users").childrenCount.toInt()
                        val summary =
                            RoomSummary(
                                id = roomId,
                                name = name,
                                hostId = host,
                                access = access,
                                mode = mode,
                                createdAt = createdAt,
                                playerCount = users,
                                hostName = null
                            )
                        summaries[roomId] = summary
                        trySend(summaries.values.sortedByDescending { it.createdAt })
                    }

                    override fun onCancelled(error: DatabaseError) { /* ignore */ }
                }
            ref.addValueEventListener(l)
            roomListeners[roomId] = l
        }

        val indexListener =
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ids = snapshot.children.mapNotNull { it.key }.toSet()
                    val removed = roomListeners.keys - ids
                    removed.forEach { id ->
                        roomsRef().child(id).removeEventListener(roomListeners.remove(id)!!)
                        summaries.remove(id)
                    }
                    ids.forEach { attachRoomListener(it) }
                    if (ids.isEmpty()) trySend(emptyList())
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            }
        indexQuery.addValueEventListener(indexListener)
        awaitClose {
            indexQuery.removeEventListener(indexListener)
            roomListeners.forEach { (id, l) -> roomsRef().child(id).removeEventListener(l) }
            roomListeners.clear()
            summaries.clear()
        }
    }

    override fun observeUserCurrentRoom(uid: String): Flow<String?> = callbackFlow {
        val ref = usersRef(uid).child("connections")
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    var current: String? = null
                    s.children.forEach { c ->
                        val v = c.getValue(String::class.java) ?: return@forEach
                        if (v.startsWith("/room/")) current = v.removePrefix("/room/")
                    }
                    trySend(current)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    override suspend fun detectCurrentRoomId(uid: String): String? {
        val conns = usersRef(uid).child("connections").get().await()
        var current: String? = null
        conns.children.forEach { c ->
            val v = c.getValue(String::class.java)
            if (v != null && v.startsWith("/room/")) current = v.removePrefix("/room/")
        }
        return current
    }

    override fun observeRoom(roomId: String): Flow<RoomState?> = callbackFlow {
        val ref = roomsRef().child(roomId)
        val listener =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (!s.exists()) {
                        trySend(null)
                        return
                    }
                    val host = s.child("host").getValue(String::class.java) ?: return
                    val accessRaw =
                        s.child("access").getValue(String::class.java) ?: "public"
                    val access = if (accessRaw ==
                        "password"
                    ) {
                        Access.PASSWORD
                    } else {
                        Access.PUBLIC
                    }
                    val mode = s.child("mode").getValue(String::class.java) ?: "normal"
                    val statusRaw =
                        s.child("status").getValue(String::class.java) ?: "WAITING"
                    val createdAt = s.child("createdAt").getValue(Long::class.java) ?: 0L
                    val startedAt = s.child("startedAt").getValue(Long::class.java)
                    val name = s.child("name").getValue(String::class.java) ?: roomId
                    val users = mutableMapOf<String, Long>()
                    s.child("users").children.forEach { u ->
                        val uid = u.key ?: return@forEach
                        val t = u.getValue(Long::class.java) ?: 0L
                        users[uid] = t
                    }
                    // Close the room from public listing once game is in progress
                    if (statusRaw == "INGAME") {
                        try {
                            publicRoomsRef().child(roomId).removeValue()
                        } catch (_: Exception) {
                        }
                    }
                    val state =
                        RoomState(
                            id = roomId,
                            name = name,
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

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun createRoom(
        hostId: String,
        access: Access,
        mode: String,
        roomName: String,
        passwordPlain: String?
    ): String {
        require(allowedModes.contains(mode)) { "Invalid mode: $mode" }
        val now = ServerValue.TIMESTAMP
        val roomId = roomsRef().push().key ?: UUID.randomUUID().toString().replace("-", "")
        val created =
            mutableMapOf<String, Any?>().apply {
                put("host", hostId)
                put("name", roomName.take(50))
                put("access", if (access == Access.PASSWORD) "password" else "public")
                put("mode", mode)
                put("status", "WAITING")
                put("createdAt", now)
                if (access == Access.PASSWORD && !passwordPlain.isNullOrBlank()) {
                    val salt = UUID.randomUUID().toString().replace("-", "")
                    put("pwdSalt", salt)
                    put("pwdHash", sha256(salt + ":" + passwordPlain))
                }
                // Users map starts empty; host will auto-join on first open
            }

        roomsRef().child(roomId).setValue(created).await()
        val indexUpdates = hashMapOf<String, Any?>()
        if (access == Access.PUBLIC) {
            indexUpdates["/publicRooms/$roomId"] = ServerValue.TIMESTAMP
        }
        indexUpdates["/userRooms/$hostId/$roomId"] = ServerValue.TIMESTAMP
        if (indexUpdates.isNotEmpty()) {
            rtdb.reference.updateChildren(indexUpdates).await()
        }
        return roomId
    }

    override suspend fun joinRoom(roomId: String, uid: String, passwordPlain: String?) {
        // Ensure user is only in one room at a time
        detectCurrentRoomId(uid)?.let { current ->
            if (current !=
                roomId
            ) {
                runCatching { leaveRoom(current, uid) }
            }
        }

        val room = roomsRef().child(roomId).get().await()
        if (!room.exists()) throw IllegalStateException("Room not found")
        val access = room.child("access").getValue(String::class.java) ?: "public"
        if (access == "password") {
            val whitelisted =
                room.child("whitelist").child(uid).getValue(Boolean::class.java) == true
            if (!whitelisted) {
                val salt = room.child("pwdSalt").getValue(String::class.java) ?: ""
                val expected = room.child("pwdHash").getValue(String::class.java) ?: ""
                val actual = sha256(salt + ":" + (passwordPlain ?: ""))
                require(actual == expected) { "Invalid room password" }
            }
        }
        val updates = hashMapOf<String, Any?>()
        updates["/rooms/$roomId/users/$uid"] = ServerValue.TIMESTAMP
        updates["/userRooms/$uid/$roomId"] =
            room.child("createdAt").value ?: ServerValue.TIMESTAMP
        rtdb.reference.updateChildren(updates).await()

        // Mark current screen connection to this room and cleanup other room connections
        try {
            val conns = usersRef(uid).child("connections").get().await()
            conns.children.forEach { c ->
                val v = c.getValue(String::class.java)
                if (v != null && v.startsWith("/room/") && v != "/room/$roomId") {
                    c.ref.removeValue()
                }
            }
        } catch (_: Exception) {
            // best-effort
        }
        val conRef = usersRef(uid).child("connections").push()
        conRef.onDisconnect().removeValue()
        conRef.setValue("/room/$roomId")
        usersRef(uid).child("lastOnline").onDisconnect().setValue(ServerValue.TIMESTAMP)

        // Auto-cleanup membership on disconnect
        roomsRef()
            .child(roomId)
            .child("users")
            .child(uid)
            .onDisconnect()
            .removeValue()
    }

    override suspend fun leaveRoom(roomId: String, uid: String) {
        val updates = hashMapOf<String, Any?>()
        updates["/rooms/$roomId/users/$uid"] = null
        updates["/userRooms/$uid/$roomId"] = null
        rtdb.reference.updateChildren(updates).await()
        // Remove presence connections that point to this room path
        try {
            val conns = usersRef(uid).child("connections").get().await()
            conns.children.forEach { c ->
                val v = c.getValue(String::class.java)
                if (v == "/room/$roomId") {
                    c.ref.removeValue()
                }
            }
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    override suspend fun startGame(roomId: String, hostId: String) {
        val roomSnap = roomsRef().child(roomId).get().await()
        val host = roomSnap.child("host").getValue(String::class.java)
        require(host == hostId) { "Only host can start" }
        val status = roomSnap.child("status").getValue(String::class.java) ?: "WAITING"
        require(status == "WAITING") { "Game already started or finished" }
        val mode = roomSnap.child("mode").getValue(String::class.java) ?: "normal"
        // Safeguard when there is a pending post-game ack list, ensure all required acks are true
        val users = roomSnap.child("users").children.mapNotNull { it.key }
        val acks = roomSnap.child("postGameAck")
        if (acks.exists()) {
            val userSet = users.toSet()
            val startedAt = roomSnap.child(
                "postGameAckMeta"
            ).child("startedAt").getValue(Long::class.java)
            // Use server time offset to avoid client clock skew
            val offset =
                try {
                    rtdb
                        .getReference(".info/serverTimeOffset")
                        .get()
                        .await()
                        .getValue(Long::class.java) ?: 0L
                } catch (
                    _: Exception
                ) {
                    0L
                }
            val serverNow = System.currentTimeMillis() + offset
            val timedOut = startedAt != null && (serverNow - startedAt) >= 20_000L
            acks.children.forEach { child ->
                val uid = child.key ?: return@forEach
                var ok = child.getValue(Boolean::class.java) ?: false
                if (uid in userSet) {
                    if (!ok) {
                        // If the user is offline (no active connections), auto-acknowledge to avoid deadlock
                        val conns =
                            try {
                                usersRef(uid).child("connections").get().await()
                            } catch (_: Exception) {
                                null
                            }
                        val online = conns?.children?.any() == true
                        if (!online || timedOut) {
                            try {
                                roomsRef()
                                    .child(roomId)
                                    .child("postGameAck")
                                    .child(uid)
                                    .setValue(true)
                                    .await()
                            } catch (_: Exception) {
                            }
                            ok = true
                        }
                    }
                    require(ok) { "All players must exit completion screen before next game" }
                } else {
                    // Orphan ack belonging to a user who already left the room; clean it up
                    rtdb.reference.child("rooms/$roomId/postGameAck/$uid").setValue(null)
                }
            }
        }

        val gameId =
            rtdb.getReference("games").push().key ?: java.util.UUID
                .randomUUID()
                .toString()
                .replace("-", "")
        val updates = hashMapOf<String, Any?>()
        updates["/games/$gameId/roomId"] = roomId
        updates["/games/$gameId/mode"] = mode
        updates["/games/$gameId/host"] = hostId
        updates["/games/$gameId/startedAt"] = ServerValue.TIMESTAMP
        updates["/games/$gameId/status"] = "INGAME"
        // Persist participant roster at game start (for history)
        users.forEach { u -> updates["/games/$gameId/participants/$u"] = true }
        // Clear post-game ack flags for new cycle: remove child entries individually
        if (acks.exists()) {
            acks.children.forEach { child ->
                val uidKey = child.key ?: return@forEach
                updates["/rooms/$roomId/postGameAck/$uidKey"] = null
            }
        }
        updates["/rooms/$roomId/currentGameId"] = gameId
        updates["/rooms/$roomId/status"] = "INGAME"
        updates["/publicRooms/$roomId"] = null
        rtdb.reference.updateChildren(updates).await()
    }

    override suspend fun setPostGameAck(roomId: String, uid: String, value: Boolean) {
        roomsRef()
            .child(roomId)
            .child("postGameAck")
            .child(uid)
            .setValue(value)
            .await()
    }

    override fun observePostGameAck(roomId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = roomsRef().child(roomId).child("postGameAck")
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val map = mutableMapOf<String, Boolean>()
                    s.children.forEach { c ->
                        val uid = c.key ?: return@forEach
                        val v = c.getValue(Boolean::class.java)
                        if (v != null) map[uid] = v
                    }
                    trySend(map)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyMap())
                }
            }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    override fun observePostGameAckMeta(roomId: String): Flow<Long?> = callbackFlow {
        val ref = roomsRef().child(roomId).child("postGameAckMeta").child("startedAt")
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    trySend(s.getValue(Long::class.java))
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    override fun observeServerTimeOffset(): Flow<Long> = callbackFlow {
        val ref = rtdb.getReference(".info/serverTimeOffset")
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    trySend(s.getValue(Long::class.java) ?: 0L)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(0L)
                }
            }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    override suspend fun disbandRoom(roomId: String, hostId: String) {
        val ref = roomsRef().child(roomId)
        val room = ref.get().await()
        if (!room.exists()) return
        val host = room.child("host").getValue(String::class.java)
        require(host == hostId) { "Only host can disband" }
        val users = room.child("users").children.mapNotNull { it.key }

        // Build updates to remove room and index references atomically
        val updates = hashMapOf<String, Any?>()
        updates["/rooms/$roomId"] = null
        updates["/publicRooms/$roomId"] = null
        users.forEach { uid -> updates["/userRooms/$uid/$roomId"] = null }
        updates["/userRooms/$hostId/$roomId"] = null
        updates["/chats/$roomId"] = null
        rtdb.reference.updateChildren(updates).await()
    }

    override suspend fun updateMode(roomId: String, hostId: String, mode: String) {
        require(allowedModes.contains(mode)) { "Invalid mode: $mode" }
        val room = roomsRef().child(roomId).get().await()
        if (!room.exists()) throw IllegalStateException("Room not found")
        val host = room.child("host").getValue(String::class.java)
        require(host == hostId) { "Only host can change mode" }
        val status = room.child("status").getValue(String::class.java) ?: "WAITING"
        require(status == "WAITING") { "Can only change mode while WAITING" }
        roomsRef()
            .child(roomId)
            .child("mode")
            .setValue(mode)
            .await()
    }

    override suspend fun whitelistInvitee(roomId: String, inviterUid: String, invitedUid: String) {
        val room = roomsRef().child(roomId).get().await()
        if (!room.exists()) throw IllegalStateException("Room not found")
        val host = room.child("host").getValue(String::class.java)
        val isMember = room.child("users").hasChild(inviterUid) || host == inviterUid
        require(isMember) { "Only members can invite" }
        whitelistRef(roomId).child(invitedUid).setValue(true).await()
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = md.digest(text.toByteArray())
        return b.joinToString("") { String.format("%02x", it) }
    }
}
