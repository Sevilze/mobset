package com.mobset.data.rooms

import kotlinx.coroutines.flow.Flow

/**
 * Real-time multiplayer room models stored in Firebase Realtime Database.
 */
data class RoomSummary(
    val id: String,
    val hostId: String,
    val access: Access,
    val mode: String,
    val createdAt: Long,
    val playerCount: Int,
)

data class RoomState(
    val id: String,
    val hostId: String,
    val access: Access,
    val mode: String,
    val status: Status,
    val createdAt: Long,
    val startedAt: Long?,
    val users: Map<String, Long>, // uid -> join timestamp
)

enum class Access { PUBLIC, PASSWORD }

enum class Status { waiting, ingame }

interface RoomsRepository {
    fun observePublicRooms(limit: Int = 50): Flow<List<RoomSummary>>
    fun observeRoom(roomId: String): Flow<RoomState?>

    suspend fun createRoom(
        roomId: String,
        hostId: String,
        access: Access,
        mode: String,
        passwordPlain: String? = null,
        enableHint: Boolean = false,
    )

    suspend fun joinRoom(roomId: String, uid: String, passwordPlain: String? = null)
    suspend fun leaveRoom(roomId: String, uid: String)
    suspend fun startGame(roomId: String, hostId: String)
}

