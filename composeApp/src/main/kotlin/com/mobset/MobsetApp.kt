package com.mobset

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MobsetApp : Application() {
    @javax.inject.Inject lateinit var presenceTracker: com.mobset.data.presence.PresenceTracker

    override fun onCreate() {
        super.onCreate()
        presenceTracker.ensureTracking()
    }
}

