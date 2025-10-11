package com.mobset.data.auth

import kotlinx.coroutines.flow.Flow

/** Simple, platform-friendly auth model */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

sealed class AuthResult {
    data object Success : AuthResult()

    data class Error(val message: String, val cause: Throwable? = null) : AuthResult()
}

interface AuthRepository {
    val currentUser: Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult

    suspend fun signOut()
}
