package com.mobset.data.rooms

import kotlinx.coroutines.flow.Flow

/**
 * Real-time multiplayer room models stored in Firebase Realtime Database.
 */
data class RoomSummary(
    val id: String,
    val name: String,
    val hostId: String,
    val access: Access,
    val mode: String,
    val createdAt: Long,
    val playerCount: Int,
    val hostName: String? = null
)

data class RoomState(
    val id: String,
    val name: String,
    val hostId: String,
    val access: Access,
    val mode: String,
    val status: Status,
    val createdAt: Long,
    val startedAt: Long?,
    // uid -> join timestamp
    val users: Map<String, Long>
)

enum class Access { PUBLIC, PASSWORD }

enum class Status { WAITING, INGAME, COMPLETED }

interface RoomsRepository {
    fun observePublicRooms(limit: Int = 50): Flow<List<RoomSummary>>

    fun observeRoom(roomId: String): Flow<RoomState?>

    // Observe inviter's current room by reading presence connections
    fun observeUserCurrentRoom(uid: String): Flow<String?>

    suspend fun detectCurrentRoomId(uid: String): String?

    suspend fun createRoom(
        hostId: String,
        access: Access,
        mode: String,
        roomName: String,
        passwordPlain: String? = null
    ): String

    suspend fun joinRoom(roomId: String, uid: String, passwordPlain: String? = null)

    suspend fun leaveRoom(roomId: String, uid: String)

    suspend fun startGame(roomId: String, hostId: String)

    suspend fun disbandRoom(roomId: String, hostId: String)

    suspend fun updateMode(roomId: String, hostId: String, mode: String)

    // Allow inviters to whitelist invitees into password-protected rooms
    suspend fun whitelistInvitee(roomId: String, inviterUid: String, invitedUid: String)

    // Post-game acknowledgement: player confirms leaving completion screen
    suspend fun setPostGameAck(roomId: String, uid: String, value: Boolean)

    // Observe post-game acknowledgements for a room (uid -> ack)
    fun observePostGameAck(roomId: String): Flow<Map<String, Boolean>>

    // Observe post-game ack meta (startedAt timestamp when acks were initialized)
    fun observePostGameAckMeta(roomId: String): Flow<Long?>

    // Observe Firebase server time offset (ms to add to System.currentTimeMillis())
    fun observeServerTimeOffset(): Flow<Long>
}
