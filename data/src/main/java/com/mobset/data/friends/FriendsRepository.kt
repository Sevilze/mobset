package com.mobset.data.friends

import kotlinx.coroutines.flow.Flow

data class Friend(val uid: String, val since: Long)

data class FriendRequest(val id: String, val fromUid: String, val at: Long)

data class RoomInvite(val id: String, val fromUid: String, val roomId: String, val at: Long)

interface FriendsRepository {
    fun observeFriends(uid: String): Flow<List<Friend>>

    fun observeIncomingRequests(uid: String): Flow<List<FriendRequest>>

    fun observeRoomInvites(uid: String): Flow<List<RoomInvite>>

    suspend fun sendFriendRequestByEmail(fromUid: String, email: String): Boolean

    suspend fun sendFriendRequest(fromUid: String, toUid: String): Boolean

    suspend fun acceptFriendRequest(uid: String, request: FriendRequest)

    suspend fun declineFriendRequest(uid: String, requestId: String)

    suspend fun sendRoomInvite(fromUid: String, toUid: String, roomId: String)

    suspend fun clearRoomInvite(uid: String, inviteId: String)
}
