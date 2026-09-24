package com.invictus.xcode

import android.app.Application
import com.invictus.xcode.di.AppContainer

class XcodeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
