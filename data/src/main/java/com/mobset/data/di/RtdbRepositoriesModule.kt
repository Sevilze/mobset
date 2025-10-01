package com.mobset.data.di

import com.mobset.data.chat.ChatRepository
import com.mobset.data.chat.ChatRtdbRepository
import com.mobset.data.rooms.RoomsRepository
import com.mobset.data.rooms.RoomsRtdbRepository
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
    abstract fun bindChatRepository(impl: ChatRtdbRepository): ChatRepository
}

