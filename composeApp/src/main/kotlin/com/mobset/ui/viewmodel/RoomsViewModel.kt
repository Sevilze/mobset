package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.presence.PresenceRepository
import com.mobset.data.presence.PresenceTracker
import com.mobset.data.rooms.Access
import com.mobset.data.rooms.RoomState
import com.mobset.data.rooms.RoomSummary
import com.mobset.data.rooms.RoomsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoomsViewModel
@Inject
constructor(
    private val auth: AuthRepository,
    private val rooms: RoomsRepository,
    private val presence: PresenceTracker,
    private val presenceRepo: PresenceRepository
) : ViewModel() {
    init {
        // Presence tracking now starts at app startup via MobsetApp.onCreate()
    }

    val currentUser = auth.currentUser

    private val refreshTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val _refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val publicRooms: StateFlow<List<RoomSummary>> =
        kotlinx.coroutines.flow
            .combine(currentUser, refreshTick) { user, _ -> user }
            .flatMapLatest { rooms.observePublicRooms() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // HostId -> display name resolved from RTDB users/$uid
    val hostNames: StateFlow<Map<String, String>> =
        publicRooms
            .map { it.map { r -> r.hostId }.distinct().toSet() }
            .flatMapLatest { ids -> presenceRepo.observeMany(ids) }
            .map { users -> users.mapValues { (_, u) -> u.displayName ?: "Unknown" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            // Bump the trigger; small delay to show progress for UX consistency
            refreshTick.value = refreshTick.value + 1
            kotlinx.coroutines.delay(600)
            _refreshing.value = false
        }
    }

    fun createPublicRoom(roomName: String, mode: String) {
        viewModelScope.launch {
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.createRoom(
                hostId = uid,
                access = Access.PUBLIC,
                mode = mode,
                roomName = roomName
            )
        }
    }

    fun createPasswordRoom(roomName: String, mode: String, password: String) {
        viewModelScope.launch {
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.createRoom(
                hostId = uid,
                access = Access.PASSWORD,
                mode = mode,
                roomName = roomName,
                passwordPlain = password
            )
        }

        // Observe current room (presence-based) and its state for auto-navigation
        val myRoomId: StateFlow<String?> =
            currentUser
                .map { it?.uid }
                .flatMapLatest { uid ->
                    if (uid ==
                        null
                    ) {
                        kotlinx.coroutines.flow.flowOf<String?>(null)
                    } else {
                        rooms.observeUserCurrentRoom(uid)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val myRoomState: StateFlow<RoomState?> =
            myRoomId
                .filterNotNull()
                .flatMapLatest { rid -> rooms.observeRoom(rid) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }
}
