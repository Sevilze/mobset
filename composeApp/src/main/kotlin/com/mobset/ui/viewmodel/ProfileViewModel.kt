package com.mobset.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.history.GameModeType
import com.mobset.data.history.PlayerMode
import com.mobset.data.stats.PlayerStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: GameHistoryRepository,
    private val statsRepository: PlayerStatsRepository
) : ViewModel() {

    data class Filters(
        val gameMode: GameModeType? = null,
        val playerMode: PlayerMode? = null
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters

    val currentUser = authRepository.currentUser.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun setGameMode(mode: GameModeType?) { _filters.value = _filters.value.copy(gameMode = mode) }
    fun setPlayerMode(mode: PlayerMode?) { _filters.value = _filters.value.copy(playerMode = mode) }

    // Recompute when either the user OR filters change
    val aggregatedStats = combine(currentUser.filterNotNull(), _filters) { user, f ->
        user.uid to f
    }.flatMapLatest { (uid, f) ->
        statsRepository.observeAggregatedStats(
            playerId = uid,
            gameMode = f.gameMode,
            playerMode = f.playerMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val games = combine(currentUser.filterNotNull(), _filters) { user, f ->
        user.uid to f
    }.flatMapLatest { (uid, f) ->
        historyRepository.observeUserGames(uid, f.gameMode, f.playerMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class WinLoss(val wins: Int, val losses: Int)

    val winLoss: Flow<WinLoss> = games.map { list ->
        val userId = currentUser.value?.uid
        if (userId == null || list.isEmpty()) return@map WinLoss(0, 0)
        var w = 0; var l = 0
        list.forEach { r -> if (userId in r.winners) w++ else l++ }
        WinLoss(w, l)
    }
}

