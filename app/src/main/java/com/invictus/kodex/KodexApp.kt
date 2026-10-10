package com.invictus.kodex

import android.app.Application
import com.invictus.kodex.core.diagnostics.GitLog
import com.invictus.kodex.core.diagnostics.CrashHandler
import com.invictus.kodex.core.git.installGitByteCounting
import com.invictus.kodex.core.git.installGitPerformanceTuning
import com.invictus.kodex.di.AppContainer
import com.invictus.kodex.ui.theme.ThemeSettings

class KodexApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        GitLog.init(this)
        ThemeSettings.init(this)
        installGitByteCounting()
        installGitPerformanceTuning()
        container = AppContainer(this)
    }
}
