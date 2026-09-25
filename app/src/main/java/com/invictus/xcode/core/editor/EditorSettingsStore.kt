package com.invictus.xcode.core.editor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.editorSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "editor_settings")

/**
 * Editor-wide settings that outlive any one tab: the chosen colour theme and the last font
 * size used, so a newly opened tab starts at the size the user left off at (plan tech stack:
 * "DataStore Preferences (settings)").
 */
class EditorSettingsStore(private val context: Context) {
    private object Keys {
        val THEME_ID = stringPreferencesKey("editor_theme_id")
        // Stored as the same raw pixel size CodeEditor.textSizePx already uses everywhere else
        // in this codebase (TabBuffer, CodeEditorView) -- no density conversion needed.
        val FONT_SIZE_PX = floatPreferencesKey("editor_font_size_px")
        val SYMBOL_BAR = stringPreferencesKey("editor_symbol_bar")
    }

    val themeId: Flow<String> = context.editorSettingsDataStore.data
        .map { it[Keys.THEME_ID] ?: EditorThemes.SYSTEM_DEFAULT }

    /** 0f = no saved preference yet; caller falls back to the editor's built-in default size. */
    val fontSizePx: Flow<Float> = context.editorSettingsDataStore.data
        .map { it[Keys.FONT_SIZE_PX] ?: 0f }

    /** Plan 3.2 "customizable symbol bar" -- the ordered list of quick-insert symbols. */
    val symbolBar: Flow<List<String>> = context.editorSettingsDataStore.data
        .map { prefs -> prefs[Keys.SYMBOL_BAR]?.let(::decodeSymbols) ?: DEFAULT_SYMBOLS }

    suspend fun setThemeId(id: String) {
        context.editorSettingsDataStore.edit { it[Keys.THEME_ID] = id }
    }

    suspend fun setFontSizePx(sizePx: Float) {
        context.editorSettingsDataStore.edit { it[Keys.FONT_SIZE_PX] = sizePx }
    }

    suspend fun setSymbolBar(symbols: List<String>) {
        context.editorSettingsDataStore.edit { it[Keys.SYMBOL_BAR] = encodeSymbols(symbols) }
    }

    companion object {
        /** \u0001 rather than a visible separator, since the symbols themselves include commas/spaces. */
        private const val SYMBOL_SEPARATOR = "\u0001"

        val DEFAULT_SYMBOLS = listOf(
            "{", "}", "(", ")", ";", "=", "\"", "'", "<", ">",
            ".", ",", ":", "_", "-", "+", "*", "/", "\\", "|",
            "&", "!", "?", "#", "[", "]",
        )

        private fun encodeSymbols(symbols: List<String>): String = symbols.joinToString(SYMBOL_SEPARATOR)

        private fun decodeSymbols(raw: String): List<String> =
            raw.split(SYMBOL_SEPARATOR).filter { it.isNotEmpty() }.ifEmpty { DEFAULT_SYMBOLS }
    }
}
