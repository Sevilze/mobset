package com.mobset.data.di

import com.mobset.data.friends.FirestoreFriendsRepository
import com.mobset.data.friends.FriendsRepository
import com.mobset.data.game.FirestoreGameRepository
import com.mobset.data.game.GameRepository
import com.mobset.data.history.FirestoreGameHistoryRepository
import com.mobset.data.history.GameHistoryRepository
import com.mobset.data.profile.FirestoreProfileRepository
import com.mobset.data.profile.ProfileRepository
import com.mobset.data.stats.FirestoreStatsRepository
import com.mobset.data.stats.PlayerStatsFromHistoryRepository
import com.mobset.data.stats.PlayerStatsRepository
import com.mobset.data.stats.StatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreRepositoriesModule {
    @Binds @Singleton
    abstract fun bindGameRepository(impl: FirestoreGameRepository): GameRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: FirestoreProfileRepository): ProfileRepository

    @Binds @Singleton
    abstract fun bindStatsRepository(impl: FirestoreStatsRepository): StatsRepository

    @Binds @Singleton
    abstract fun bindGameHistoryRepository(
        impl: FirestoreGameHistoryRepository
    ): GameHistoryRepository

    @Binds @Singleton
    abstract fun bindPlayerStatsRepository(
        impl: PlayerStatsFromHistoryRepository
    ): PlayerStatsRepository

    @Binds @Singleton
    abstract fun bindFriendsRepository(impl: FirestoreFriendsRepository): FriendsRepository
}
