package com.hereliesaz.mademedance

import android.app.Application
import com.hereliesaz.mademedance.settings.SettingsStore

class MadeMeDanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
    }
}
