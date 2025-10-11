package com.mobset.data.rtdb

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealtimeDatabaseModule {
    @Provides
    @Singleton
    fun provideFirebaseDatabase(app: Application): FirebaseDatabase {
        // Ensure default app is initialized by AuthModule; get instance and enable persistence for offline resilience.
        val db = FirebaseDatabase.getInstance()
        // Keep default persistence; RTDB handles small local cache. Avoid heavy keepSynced to minimize bandwidth.
        db.setPersistenceEnabled(true)
        return db
    }
}
