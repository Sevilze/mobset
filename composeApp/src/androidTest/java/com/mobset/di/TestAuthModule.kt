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
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.mobset.data.di.AuthBinderModule::class]
)
object TestAuthModule {
    @Provides
    @Singleton
    fun provideFakeAuthRepository(): AuthRepository = FakeAuthRepository()
}

object TestAuthState {
    val state = MutableStateFlow<AuthUser?>(null)
    fun setUser(user: AuthUser?) { state.value = user }
}

class FakeAuthRepository : AuthRepository {
    override val currentUser: Flow<AuthUser?> = TestAuthState.state as StateFlow<AuthUser?>

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        TestAuthState.state.value = AuthUser(uid = "test", displayName = "Tester", email = "t@example.com", photoUrl = null)
        return AuthResult.Success
    }

    override suspend fun signOut() {
        TestAuthState.state.value = null
    }

    fun setUser(user: AuthUser?) { TestAuthState.state.value = user }
}

