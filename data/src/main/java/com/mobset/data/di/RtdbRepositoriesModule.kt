package com.mobset.data.di

import com.mobset.data.chat.ChatRepository
import com.mobset.data.chat.ChatRtdbRepository
import com.mobset.data.multiplayer.MultiplayerGameRepository
import com.mobset.data.multiplayer.MultiplayerGameRtdbRepository
import com.mobset.data.rooms.RoomsRepository
import com.mobset.data.rooms.RoomsRtdbRepository
import com.mobset.data.presence.PresenceRepository
import com.mobset.data.presence.RtdbPresenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RtdbRepositoriesModule {
    @Binds @Singleton
    abstract fun bindRoomsRepository(impl: RoomsRtdbRepository): RoomsRepository

    @Binds @Singleton
    abstract fun bindPresenceRepository(impl: RtdbPresenceRepository): PresenceRepository

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRtdbRepository): ChatRepository

    @Binds @Singleton
    abstract fun bindMultiplayerGameRepository(impl: MultiplayerGameRtdbRepository): MultiplayerGameRepository
}

