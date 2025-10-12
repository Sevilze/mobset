package com.mobset.data.profile

import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreProfileRepository
@Inject
constructor(
    private val db: FirebaseFirestore,
    private val rtdb: FirebaseDatabase
) : ProfileRepository {
    private fun col() = db.collection("profiles")

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        var fs: Map<String, Any?>? = null
        var rtdbName: String? = null

        fun emitMerged() {
            if (fs == null && rtdbName == null) {
                trySend(null)
                return
            }
            val data = fs
            val profile =
                UserProfile(
                    uid = uid,
                    displayName = rtdbName ?: (data?.get("displayName") as? String),
                    email = data?.get("email") as? String,
                    photoUrl = data?.get("photoUrl") as? String,
                    gamesPlayed = (data?.get("gamesPlayed") as? Number)?.toInt() ?: 0,
                    bestTimeMs = (data?.get("bestTimeMs") as? Number)?.toLong() ?: 0L,
                    setsFound = (data?.get("setsFound") as? Number)?.toInt() ?: 0,
                    themeDynamic = data?.get("themeDynamic") as? Boolean,
                    themeAccentHex = data?.get("themeAccentHex") as? String,
                    themeTemplate = data?.get("themeTemplate") as? String,
                    cardColorHex1 = data?.get("cardColorHex1") as? String,
                    cardColorHex2 = data?.get("cardColorHex2") as? String,
                    cardColorHex3 = data?.get("cardColorHex3") as? String
                )
            trySend(profile)
        }

        val fsReg =
            col().document(uid).addSnapshotListener { snap, _ ->
                fs = snap?.data
                emitMerged()
            }

        val userRef = rtdb.getReference("users").child(uid).child("name")
        val rtdbListener =
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    rtdbName = snapshot.getValue(String::class.java)
                    emitMerged()
                }

                override fun onCancelled(error: DatabaseError) { /* ignore */ }
            }
        userRef.addValueEventListener(rtdbListener)

        awaitClose {
            fsReg.remove()
            userRef.removeEventListener(rtdbListener)
        }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        val doc = col().document(profile.uid)
        val map =
            mapOf(
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
        // Write to Firestore
        doc.set(map).await()
    }
}
