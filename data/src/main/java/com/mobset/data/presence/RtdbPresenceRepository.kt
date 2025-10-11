package com.mobset.data.presence

import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class RtdbPresenceRepository
@Inject
constructor(
    private val rtdb: FirebaseDatabase,
    private val firestore: FirebaseFirestore
) : PresenceRepository {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private fun userRef(uid: String) = rtdb.getReference("users").child(uid)

    private fun profileDoc(uid: String) = firestore.collection("profiles").document(uid)

    override fun observe(uid: String): Flow<PresenceUser?> =
        combine(rtdbPresence(uid), fsProfile(uid)) { p, prof ->
            if (p == null && prof == null) {
                null
            } else {
                PresenceUser(
                    uid = uid,
                    isOnline = p?.first == true,
                    lastOnline = p?.second,
                    displayName = prof?.get("displayName") as? String,
                    email = prof?.get("email") as? String,
                    photoUrl = prof?.get("photoUrl") as? String
                )
            }
        }.distinctUntilChanged()

    override fun observeMany(uids: Set<String>): Flow<Map<String, PresenceUser>> = callbackFlow {
        if (uids.isEmpty()) {
            trySend(emptyMap())
            awaitClose { }
            return@callbackFlow
        }
        val current = mutableMapOf<String, PresenceUser>()
        val presenceFlows = mutableMapOf<String, Flow<Pair<Boolean, Long?>?>>()
        val profileFlows = mutableMapOf<String, Flow<Map<String, Any?>?>>()

        fun emit(uid: String, pres: Pair<Boolean, Long?>?, prof: Map<String, Any?>?) {
            val merged =
                if (pres == null && prof == null) {
                    null
                } else {
                    PresenceUser(
                        uid = uid,
                        isOnline = pres?.first == true,
                        lastOnline = pres?.second,
                        displayName = prof?.get("displayName") as? String,
                        email = prof?.get("email") as? String,
                        photoUrl = prof?.get("photoUrl") as? String
                    )
                }
            if (merged == null) {
                current.remove(uid)
            } else {
                current[uid] = merged
            }
            trySend(current.toMap())
        }

        val jobs =
            uids.map { uid ->
                val p = rtdbPresence(uid)
                val f = fsProfile(uid)
                presenceFlows[uid] = p
                profileFlows[uid] = f
                scope.launch {
                    combine(p, f) { pres, prof -> pres to prof }
                        .collect { (pres, prof) -> emit(uid, pres, prof) }
                }
            }

        awaitClose { jobs.forEach { it.cancel() } }
    }

    private fun rtdbPresence(uid: String): Flow<Pair<Boolean, Long?>?> = callbackFlow {
        val ref = userRef(uid)
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (!s.exists()) {
                        trySend(null)
                        return
                    }
                    val connections = s.child("connections").childrenCount.toInt()
                    val online = connections > 0
                    val last = s.child("lastOnline").getValue(Long::class.java)
                    trySend(online to last)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    private fun fsProfile(uid: String): Flow<Map<String, Any?>?> = callbackFlow {
        val reg =
            profileDoc(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.data)
            }
        awaitClose { reg.remove() }
    }
}
