package com.invictus.xcode

import android.app.Application
import com.invictus.xcode.core.diagnostics.CrashHandler
import com.invictus.xcode.di.AppContainer
import com.invictus.xcode.ui.theme.ThemeSettings

class XcodeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        ThemeSettings.init(this)
        container = AppContainer(this)
    }
}
