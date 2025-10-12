package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.friends.Friend
import com.mobset.data.friends.FriendRequest
import com.mobset.data.friends.FriendsRepository
import com.mobset.data.friends.RoomInvite
import com.mobset.data.presence.PresenceRepository
import com.mobset.data.presence.PresenceUser
import com.mobset.data.rooms.RoomsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModel
@Inject
constructor(
    private val auth: AuthRepository,
    private val friends: FriendsRepository,
    private val presence: PresenceRepository,
    private val rooms: RoomsRepository
) : ViewModel() {
    val currentUser = auth.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    private val myUidFlow: Flow<String> = currentUser.filterNotNull().map { it.uid }

    val currentRoomId: StateFlow<String?> =
        myUidFlow
            .flatMapLatest { uid -> rooms.observeUserCurrentRoom(uid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val friendsList: StateFlow<List<Friend>> =
        myUidFlow
            .flatMapLatest { friends.observeFriends(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friendProfiles =
        friendsList
            .map { it.map { f -> f.uid }.toSet() }
            .flatMapLatest { ids -> presence.observeMany(ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val incomingRequests: StateFlow<List<FriendRequest>> =
        myUidFlow
            .flatMapLatest { friends.observeIncomingRequests(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequestProfiles: StateFlow<Map<String, PresenceUser>> =
        incomingRequests
            .map { reqs -> reqs.map { it.fromUid }.toSet() }
            .flatMapLatest { ids -> presence.observeMany(ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val roomInvites: StateFlow<List<RoomInvite>> =
        myUidFlow
            .flatMapLatest { friends.observeRoomInvites(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inviteProfiles: StateFlow<Map<String, PresenceUser>> =
        roomInvites
            .map { invs -> invs.map { it.fromUid }.toSet() }
            .flatMapLatest { ids -> presence.observeMany(ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addFriendByEmail(email: String) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            friends.sendFriendRequestByEmail(me, email)
        }
    }

    fun addFriendByUid(uid: String) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            friends.sendFriendRequest(me, uid)
        }
    }

    fun accept(req: FriendRequest) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            friends.acceptFriendRequest(me, req)
        }
    }

    fun decline(requestId: String) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            friends.declineFriendRequest(me, requestId)
        }
    }

    fun invite(toUid: String) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            val room = currentRoomId.value ?: return@launch
            friends.sendRoomInvite(me, toUid, room)
        }
    }

    fun clearInvite(inviteId: String) {
        viewModelScope.launch {
            val me = currentUser.value?.uid ?: return@launch
            friends.clearRoomInvite(me, inviteId)
        }
    }
}
