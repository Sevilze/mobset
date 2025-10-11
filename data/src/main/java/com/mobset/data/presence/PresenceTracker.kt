package com.mobset.data.presence

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.mobset.data.auth.AuthRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class PresenceTracker
@Inject
constructor(
    private val auth: AuthRepository,
    private val db: FirebaseDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var started = false
    private var connRef: DatabaseReference? = null
    private var onDisconnectSet = false

    fun ensureTracking() {
        if (started) return
        started = true
        auth.currentUser
            .filterNotNull()
            .onEach { user ->
                val uid = user.uid
                val infoRef = db.getReference(".info/connected")
                val userRef = db.getReference("users").child(uid)
                val connections = userRef.child("connections")
                val connectionId = connections.push().key ?: UUID.randomUUID().toString()
                connRef = connections.child(connectionId)

                connRef?.onDisconnect()?.removeValue()
                userRef.child("lastOnline").onDisconnect().setValue(ServerValue.TIMESTAMP)

                infoRef.addValueEventListener(
                    object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val connected = snapshot.getValue(Boolean::class.java) == true
                            if (connected) {
                                // Mark this device connection only when connected
                                connRef?.setValue("android")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    }
                )
            }.launchIn(scope)
    }
}
