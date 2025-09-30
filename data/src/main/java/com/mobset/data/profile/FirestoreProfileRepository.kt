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
                        setsFound = (data["setsFound"] as? Number)?.toInt() ?: 0,
                        themeDynamic = data["themeDynamic"] as? Boolean,
                        themeAccentHex = data["themeAccentHex"] as? String,
                        themeTemplate = data["themeTemplate"] as? String,
                        cardColorHex1 = data["cardColorHex1"] as? String,
                        cardColorHex2 = data["cardColorHex2"] as? String,
                        cardColorHex3 = data["cardColorHex3"] as? String
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
            "setsFound" to profile.setsFound,
            "themeDynamic" to profile.themeDynamic,
            "themeAccentHex" to profile.themeAccentHex,
            "themeTemplate" to profile.themeTemplate,
            "cardColorHex1" to profile.cardColorHex1,
            "cardColorHex2" to profile.cardColorHex2,
            "cardColorHex3" to profile.cardColorHex3
        )
        doc.set(map).await()
    }
}

