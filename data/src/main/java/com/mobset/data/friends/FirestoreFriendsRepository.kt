package com.mobset.data.friends

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.mobset.data.rooms.RoomsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreFriendsRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val rooms: RoomsRepository,
) : FriendsRepository {

    private fun friendsCol(uid: String) = db.collection("friends").document(uid).collection("list")
    private fun reqIncomingCol(uid: String) = db.collection("friends").document(uid).collection("requests_incoming")
    private fun reqOutgoingCol(uid: String) = db.collection("friends").document(uid).collection("requests_outgoing")
    private fun roomInvitesCol(uid: String) = db.collection("invites").document(uid).collection("room")
    private fun DocumentSnapshot.millis(field: String): Long {
        this.getTimestamp(field)?.let { return it.toDate().time }
        this.getLong(field)?.let { return it }
        return 0L
    }
    
    override fun observeFriends(uid: String): Flow<List<Friend>> = callbackFlow {
        val reg = friendsCol(uid).addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { d ->
                val since = d.millis("since")
                if (since == 0L) return@mapNotNull null
                Friend(uid = d.id, since = since)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override fun observeIncomingRequests(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg = reqIncomingCol(uid).addSnapshotListener { snap, _ ->
            val list = snap?.documents?.map { d ->
                FriendRequest(id = d.id, fromUid = d.getString("fromUid") ?: d.id, at = d.millis("at"))
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override fun observeRoomInvites(uid: String): Flow<List<RoomInvite>> = callbackFlow {
        val reg = roomInvitesCol(uid).addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { d ->
                val from = d.getString("fromUid") ?: return@mapNotNull null
                val room = d.getString("roomId") ?: return@mapNotNull null
                RoomInvite(id = d.id, fromUid = from, roomId = room, at = d.millis("at"))
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun sendFriendRequestByEmail(fromUid: String, email: String): Boolean {
        val q = db.collection("profiles").whereEqualTo("email", email).limit(1).get().await()
        val toUid = q.documents.firstOrNull()?.id ?: return false
        return sendFriendRequest(fromUid, toUid)
    }

    override suspend fun sendFriendRequest(fromUid: String, toUid: String): Boolean {
        if (fromUid == toUid) return false
        reqOutgoingCol(fromUid).document(toUid).set(mapOf(
            "toUid" to toUid,
            "at" to FieldValue.serverTimestamp()
        )).await()
        reqIncomingCol(toUid).document(fromUid).set(mapOf(
            "fromUid" to fromUid,
            "at" to FieldValue.serverTimestamp()
        )).await()
        return true
    }

    override suspend fun acceptFriendRequest(uid: String, request: FriendRequest) {
        // Populate both friend lists, clear requests
        val other = request.fromUid
        val now = FieldValue.serverTimestamp()
        friendsCol(uid).document(other).set(mapOf("since" to now)).await()
        friendsCol(other).document(uid).set(mapOf("since" to now)).await()
        reqIncomingCol(uid).document(request.id).delete().await()
        reqOutgoingCol(other).document(uid).delete().await()
    }

    override suspend fun declineFriendRequest(uid: String, requestId: String) {
        reqIncomingCol(uid).document(requestId).delete().await()
    }

    override suspend fun sendRoomInvite(fromUid: String, toUid: String, roomId: String) {
        val current = rooms.detectCurrentRoomId(fromUid)
        require(current == roomId) { "You must be in the room to invite" }
        val roomState = rooms.observeRoom(roomId).first()
        require(roomState?.status?.name == "waiting") { "Can only invite while the room is in waiting state" }

        roomInvitesCol(toUid).add(mapOf(
            "fromUid" to fromUid,
            "roomId" to roomId,
            "at" to FieldValue.serverTimestamp()
        )).await()
        // Whitelist the invitee in RTDB so they can bypass password (if the room is password-protected)
        try {
            rooms.whitelistInvitee(roomId = roomId, inviterUid = fromUid, invitedUid = toUid)
        } catch (_: Exception) {
            // best-effort; invite will still show, but join may require password if whitelist write fails
        }
    }

    override suspend fun clearRoomInvite(uid: String, inviteId: String) {
        roomInvitesCol(uid).document(inviteId).delete().await()
    }
}
