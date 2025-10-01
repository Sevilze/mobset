package com.mobset.data.chat

import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRtdbRepository @Inject constructor(
    private val rtdb: FirebaseDatabase
) : ChatRepository {

    private fun chatRef(roomId: String): DatabaseReference = rtdb.getReference("chats").child(roomId)

    override fun observeRoomChat(roomId: String, limit: Int): Flow<List<ChatMessage>> = callbackFlow {
        val query = chatRef(roomId).orderByChild("time").limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { s ->
                    val id = s.key ?: return@mapNotNull null
                    val user = s.child("user").getValue(String::class.java) ?: return@mapNotNull null
                    val msg = s.child("message").getValue(String::class.java) ?: return@mapNotNull null
                    val t = s.child("time").getValue(Long::class.java) ?: 0L
                    ChatMessage(id = id, userId = user, message = msg, time = t)
                }
                trySend(list.sortedBy { it.time })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    override suspend fun sendMessage(roomId: String, userId: String, message: String) {
        val m = hashMapOf<String, Any?>(
            "user" to userId,
            "message" to message,
            "time" to ServerValue.TIMESTAMP
        )
        chatRef(roomId).push().setValue(m).await()
    }
}

