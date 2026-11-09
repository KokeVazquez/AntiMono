package com.antimono.app

import android.app.Application
import com.antimono.app.data.PreferencesManager

class AntiMonoApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
    }

    companion object {
        lateinit var instance: AntiMonoApp
            private set
    }
}
