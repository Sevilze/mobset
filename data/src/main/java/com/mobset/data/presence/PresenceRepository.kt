package com.mobset.data.presence

import kotlinx.coroutines.flow.Flow

data class PresenceUser(
    val uid: String,
    val isOnline: Boolean,
    val lastOnline: Long?,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

interface PresenceRepository {
    fun observe(uid: String): Flow<PresenceUser?>

    fun observeMany(uids: Set<String>): Flow<Map<String, PresenceUser>>
}
