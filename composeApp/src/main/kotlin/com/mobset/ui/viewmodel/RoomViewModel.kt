package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.chat.ChatMessage
import com.mobset.data.chat.ChatRepository
import com.mobset.data.rooms.RoomState
import com.mobset.data.rooms.RoomsRepository
import com.mobset.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val rooms: RoomsRepository,
    private val chat: ChatRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    val currentUser = auth.currentUser

    private val roomId = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val room: StateFlow<RoomState?> = combine(roomId, refreshTick) { id, _ -> id }
        .filterNotNull()
        .flatMapLatest { id -> rooms.observeRoom(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessage>> = combine(roomId, refreshTick) { id, _ -> id }
        .filterNotNull()
        .flatMapLatest { id -> chat.observeRoomChat(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of uid -> display name for users seen in the room (players + chat authors + host)
    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames: StateFlow<Map<String, String>> = _userNames

    private val nameJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init {
        // Observe room and messages to lazily fetch display names
        viewModelScope.launch {
            room.collect { s ->
                val ids = buildSet {
                    s?.users?.keys?.let { addAll(it) }
                    s?.hostId?.let { add(it) }
                }
                ids.forEach { ensureNameSubscription(it) }
            }
        }
        viewModelScope.launch {
            messages.collect { list ->
                list.forEach { ensureNameSubscription(it.userId) }
            }
        }
    }

    private fun ensureNameSubscription(uid: String) {
        if (nameJobs.containsKey(uid)) return
        nameJobs[uid] = viewModelScope.launch {
            profiles.observeProfile(uid).collect { p ->
                val name = p?.displayName ?: uid
                _userNames.value = _userNames.value + (uid to name)
            }
        }
    }

    fun setRoom(id: String) { roomId.value = id }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            refreshTick.value = refreshTick.value + 1
            kotlinx.coroutines.delay(600)
            _refreshing.value = false
        }
    }

    fun join(password: String? = null) {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.joinRoom(id, uid, password)
        }
    }

    fun leave() {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.leaveRoom(id, uid)
        }
    }

    fun start() {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.startGame(id, uid)
        }
    }

    fun disband() {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.disbandRoom(id, uid)
        }
    }

    fun send(text: String) {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            chat.sendMessage(id, uid, text)
        }
    }
}

