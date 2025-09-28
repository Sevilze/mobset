package com.mobset.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.let { u ->
                AuthUser(
                    uid = u.uid,
                    displayName = u.displayName,
                    email = u.email,
                    photoUrl = u.photoUrl?.toString()
                )
            })
        }
        kotlin.runCatching { firebaseAuth.addAuthStateListener(listener) }
        awaitClose { kotlin.runCatching { firebaseAuth.removeAuthStateListener(listener) } }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            AuthResult.Success
        } catch (t: Throwable) {
            AuthResult.Error(
                message = t.message ?: "Sign-in failed",
                cause = t
            )
        }
    }

    override suspend fun signOut() {
        kotlin.runCatching { firebaseAuth.signOut() }
    }
}

