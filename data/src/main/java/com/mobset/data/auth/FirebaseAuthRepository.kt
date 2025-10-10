package com.mobset.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mobset.data.profile.ProfileRepository
import com.mobset.data.profile.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val profileRepository: ProfileRepository,
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
            val result = firebaseAuth.signInWithCredential(credential).await()
            // Bootstrap default profile immediately using Google account info
            firebaseAuth.currentUser?.let { u ->
                val display = (u.displayName ?: ("Player-" + u.uid.takeLast(4))).take(16)
                val email = u.email
                val photo = u.photoUrl?.toString()
                val profile = UserProfile(
                    uid = u.uid,
                    displayName = display,
                    email = email,
                    photoUrl = photo,
                )
                kotlin.runCatching { profileRepository.upsertProfile(profile) }
            }
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

