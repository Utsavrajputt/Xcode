package com.invictus.xcode.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How the app picks light vs dark. SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Global, persisted appearance settings (SharedPreferences — no extra libraries). */
object ThemeSettings {
    private const val PREFS = "xcode_theme"
    private const val KEY_THEME = "app_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_AMOLED = "amoled"

    var theme by mutableStateOf(AppTheme.Aurora); private set
    var mode by mutableStateOf(ThemeMode.SYSTEM); private set
    var amoled by mutableStateOf(false); private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val name = p.getString(KEY_THEME, AppTheme.Aurora.name) ?: AppTheme.Aurora.name
        theme = AppTheme.values().firstOrNull { it.name == name } ?: AppTheme.Aurora
        mode = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        amoled = p.getBoolean(KEY_AMOLED, false)
    }

    fun setTheme(value: AppTheme) {
        theme = value
        prefs?.edit()?.putString(KEY_THEME, value.name)?.apply()
    }

    fun setMode(value: ThemeMode) {
        mode = value
        prefs?.edit()?.putString(KEY_MODE, value.name)?.apply()
    }

    fun setAmoled(value: Boolean) {
        amoled = value
        prefs?.edit()?.putBoolean(KEY_AMOLED, value)?.apply()
    }
}

/** Lets any screen open the app-theme picker without threading callbacks through the nav graph. */
object ThemePickerState {
    var visible by mutableStateOf(false)
}
