package com.mobset.di

import com.mobset.data.auth.AuthRepository
import com.mobset.data.auth.AuthResult
import com.mobset.data.auth.AuthUser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.mobset.data.di.AuthBinderModule::class]
)
@InstallIn(SingletonComponent::class)
object TestAuthModule {
    @Provides
    @Singleton
    fun provideFakeAuthRepository(): AuthRepository = FakeAuthRepository()
}

class FakeAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthUser?>(null)
    override val currentUser: Flow<AuthUser?> = state

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        state.value = AuthUser(uid = "test", displayName = "Tester", email = "t@example.com", photoUrl = null)
        return AuthResult.Success
    }

    override suspend fun signOut() {
        state.value = null
    }

    fun setUser(user: AuthUser?) { state.value = user }
}

