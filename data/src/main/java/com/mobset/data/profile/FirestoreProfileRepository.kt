package com.mobset.data.profile

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreProfileRepository @Inject constructor(
    private val db: FirebaseFirestore
) : ProfileRepository {

    private fun col() = db.collection("profiles")

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = col().document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val data = snap?.data
            if (data == null) {
                trySend(null)
                return@addSnapshotListener
            } else {
                trySend(
                    UserProfile(
                        uid = uid,
                        displayName = data["displayName"] as? String,
                        email = data["email"] as? String,
                        photoUrl = data["photoUrl"] as? String,
                        gamesPlayed = (data["gamesPlayed"] as? Number)?.toInt() ?: 0,
                        bestTimeMs = (data["bestTimeMs"] as? Number)?.toLong() ?: 0L,
                        setsFound = (data["setsFound"] as? Number)?.toInt() ?: 0
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        val doc = col().document(profile.uid)
        val map = mapOf(
            "displayName" to profile.displayName,
            "email" to profile.email,
            "photoUrl" to profile.photoUrl,
            "gamesPlayed" to profile.gamesPlayed,
            "bestTimeMs" to profile.bestTimeMs,
            "setsFound" to profile.setsFound
        )
        doc.set(map).await()
    }
}

