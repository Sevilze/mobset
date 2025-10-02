package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.rooms.Access
import com.mobset.data.rooms.RoomSummary
import com.mobset.data.rooms.RoomsRepository
import com.mobset.data.presence.PresenceTracker
import com.mobset.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val rooms: RoomsRepository,
    private val presence: PresenceTracker,
    private val profiles: ProfileRepository,
) : ViewModel() {

    init {
        presence.ensureTracking()
    }

    val currentUser = auth.currentUser

    private val refreshTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val _refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val publicRooms: StateFlow<List<RoomSummary>> = kotlinx.coroutines.flow.combine(currentUser, refreshTick) { user, _ -> user }
        .flatMapLatest { rooms.observePublicRooms() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cache of hostId -> display name for visible public rooms
    private val _hostNames = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val hostNames: StateFlow<Map<String, String>> = _hostNames

    private val nameJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init {
        viewModelScope.launch {
            publicRooms.collect { list ->
                list.map { it.hostId }.distinct().forEach { uid ->
                    if (!nameJobs.containsKey(uid)) {
                        nameJobs[uid] = launch {
                            profiles.observeProfile(uid).collect { p ->
                                val name = p?.displayName ?: uid
                                _hostNames.value = _hostNames.value + (uid to name)
                            }
                        }
                    }
                }
            }
        }
    }

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
            rooms.createRoom(hostId = uid, access = Access.PUBLIC, mode = mode, roomName = roomName)
        }
    }

    fun createPasswordRoom(roomName: String, mode: String, password: String) {
        viewModelScope.launch {
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.createRoom(hostId = uid, access = Access.PASSWORD, mode = mode, roomName = roomName, passwordPlain = password)
        }
    }
}

