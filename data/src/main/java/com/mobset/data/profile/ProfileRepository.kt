package com.mobset.data.profile

import kotlinx.coroutines.flow.Flow

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val gamesPlayed: Int = 0,
    val bestTimeMs: Long = 0,
    val setsFound: Int = 0
)

interface ProfileRepository {
    fun observeProfile(uid: String): Flow<UserProfile?>
    suspend fun upsertProfile(profile: UserProfile)
}

