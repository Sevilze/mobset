package com.mobset.data.di

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.mobset.data.auth.AuthRepository
import com.mobset.data.auth.FirebaseAuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBinderModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(app: Application): FirebaseAuth {
        // Ensure Firebase is initialized if configuration is present
        kotlin.runCatching { FirebaseApp.initializeApp(app) }
        return FirebaseAuth.getInstance()
    }
}

