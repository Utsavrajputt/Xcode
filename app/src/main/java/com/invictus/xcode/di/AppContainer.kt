package com.invictus.xcode.di

import android.content.Context
import com.invictus.xcode.core.fs.StoragePermission

/**
 * Manual dependency container (no Hilt/Koin, same approach as xmd).
 * Add each new app-wide dependency here as a `val` (use `by lazy` for heavy ones);
 * ViewModels pull what they need from it through their Factory.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val storagePermission: StoragePermission by lazy { StoragePermission(appContext) }
}
