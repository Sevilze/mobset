package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.chat.ChatMessage
import com.mobset.data.chat.ChatRepository
import com.mobset.data.presence.PresenceRepository
import com.mobset.data.rooms.RoomState
import com.mobset.data.rooms.RoomsRepository
import com.mobset.data.rooms.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModel
@Inject
constructor(
    private val auth: AuthRepository,
    private val rooms: RoomsRepository,
    private val chat: ChatRepository,
    private val presenceRepo: PresenceRepository,
    private val presence: com.mobset.data.presence.PresenceTracker
) : ViewModel() {
    val currentUser = auth.currentUser

    private val roomId = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val room: StateFlow<RoomState?> =
        combine(roomId, refreshTick) { id, _ -> id }
            .filterNotNull()
            .flatMapLatest { id -> rooms.observeRoom(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Observe post-game acknowledgements for enabling Start button only when all current members acknowledged
    private val acks: StateFlow<Map<String, Boolean>> =
        roomId
            .filterNotNull()
            .flatMapLatest { id -> rooms.observePostGameAck(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Observe ack meta (startedAt) to allow 20s timeout override
    private val ackStartedAt: StateFlow<Long?> =
        roomId
            .filterNotNull()
            .flatMapLatest { id -> rooms.observePostGameAckMeta(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Server time offset and ticking clock for timeout computation based on server time
    private val serverOffset: StateFlow<Long> =
        rooms
            .observeServerTimeOffset()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val serverNowTick: Flow<Long> =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        }

    val canStartNext: StateFlow<Boolean> =
        combine(room, acks, ackStartedAt, serverOffset, serverNowTick) {
                s,
                ackMap,
                startedAt,
                offset,
                now
            ->
            if (s == null) return@combine false
            if (s.status != Status.WAITING) return@combine false
            if (ackMap.isEmpty()) return@combine true
            val currentUsers = s.users.keys
            val serverNow = now + offset
            val timedOut = startedAt != null && (serverNow - startedAt) >= 20_000L
            if (timedOut) return@combine true
            // Only consider acks that exist; all existing acks for current users must be true
            ackMap
                .filter { (uid, _) -> currentUsers.contains(uid) }
                .all { (_, v) -> v }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val messages: StateFlow<List<ChatMessage>> =
        combine(roomId, refreshTick) { id, _ -> id }
            .filterNotNull()
            .flatMapLatest { id -> chat.observeRoomChat(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userNames: StateFlow<Map<String, String>> =
        combine(room, messages) { s, msgs ->
            val ids =
                buildSet {
                    s?.users?.keys?.let { addAll(it) }
                    s?.hostId?.let { add(it) }
                    msgs.forEach { add(it.userId) }
                }
            ids
        }.flatMapLatest { ids -> presenceRepo.observeMany(ids) }
            .map { users -> users.mapValues { (_, u) -> u.displayName ?: "Unknown" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setRoom(id: String) {
        roomId.value = id
    }

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

    fun updateMode(mode: String) {
        viewModelScope.launch {
            val id = roomId.value ?: return@launch
            val uid = auth.currentUser.firstOrNull()?.uid ?: return@launch
            rooms.updateMode(id, uid, mode)
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
