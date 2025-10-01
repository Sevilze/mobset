package com.mobset.data.chat

import kotlinx.coroutines.flow.Flow

data class ChatMessage(
    val id: String,
    val userId: String,
    val message: String,
    val time: Long
)

interface ChatRepository {
    fun observeRoomChat(roomId: String, limit: Int = 200): Flow<List<ChatMessage>>
    suspend fun sendMessage(roomId: String, userId: String, message: String)
}

