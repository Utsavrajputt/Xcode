package com.invictus.xcode.core.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File

/**
 * Loads the bundled TextMate grammars + themes once, then hands out languages and colour
 * schemes for editors. Sora's registries are process-wide singletons, so this is too.
 */
class TextMateSupport(private val context: Context) {

    private val lock = Any()

    /** Words the user has typed before; offered by autocomplete in every file. */
    val userWords: UserWordStore by lazy { UserWordStore(context) }

    private val _ready = MutableStateFlow(false)

    /** True once grammars and themes are loaded. Until then editors show plain text. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Idempotent. Parsing the grammar list is disk work, so it runs off the main thread. */
    suspend fun ensureLoaded() {
        if (_ready.value) return
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (_ready.value) return@synchronized
                val files = FileProviderRegistry.getInstance()
                files.addFileProvider(AssetsFileResolver(context.applicationContext.assets))
                loadTheme(LIGHT_THEME, dark = false)
                loadTheme(DARK_THEME, dark = true)
                EditorThemes.ALL.forEach { loadTheme(it.assetName, it.isDark) }
                ThemeRegistry.getInstance().setTheme(LIGHT_THEME)
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                _ready.value = true
            }
        }
    }

    private fun loadTheme(name: String, dark: Boolean) {
        val path = "textmate/$name.json"
        val source = IThemeSource.fromInputStream(
            FileProviderRegistry.getInstance().tryGetInputStream(path),
            path,
            null,
        )
        ThemeRegistry.getInstance().loadTheme(ThemeModel(source, name).apply { isDark = dark })
    }

    /**
     * Applies the theme and (for known file types) the language to [editor].
     * Order matters: the theme must be active before the language is created.
     * Returns false if highlighting could not be applied; the editor then stays plain.
     *
     * [themeId] is [EditorThemes.SYSTEM_DEFAULT] (mirror the app's own light/dark, the
     * pre-picker behavior) or one of [EditorThemes.ALL]; an unknown id falls back the same way.
     */
    fun applyTo(
        editor: CodeEditor,
        file: File,
        dark: Boolean,
        themeId: String = EditorThemes.DEFAULT_ID,
    ): Boolean {
        if (!_ready.value) return false
        return try {
            val themeName = EditorThemes.resolve(themeId, dark)?.assetName ?: if (dark) DARK_THEME else LIGHT_THEME
            ThemeRegistry.getInstance().setTheme(themeName)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance()).also { scheme ->
                // Theme selection colours are often nearly the background; use a clear, opaque accent instead.
                val isDark = EditorThemes.resolve(themeId, dark)?.isDark ?: dark
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND,
                    if (isDark) 0xFF2E5CA8.toInt() else 0xFFA9CCFF.toInt(),
                )
            }
            val scope = LanguageRegistry.scopeFor(file)
            if (scope != null) {
                val language = TextMateLanguage.create(scope, true)
                language.setCompleterKeywords(completionWords(scope))
                editor.setEditorLanguage(language)
            } else {
                // No grammar for this file: still get word-based completion instead of none.
                editor.setEditorLanguage(PlainTextLanguage(userWords.words()))
            }
            true
        } catch (_: Exception) {
            // A bad grammar must never take the editor down with it.
            false
        }
    }

    private fun completionWords(scope: String): Array<String> =
        (LanguageKeywords.forScope(scope) + userWords.words()).distinct().toTypedArray()

    /** Swaps in the latest learned words without recreating the language (keeps highlighting state). */
    fun refreshCompletionWords(editor: CodeEditor, file: File) {
        runCatching {
            when (val language = editor.editorLanguage) {
                is TextMateLanguage -> LanguageRegistry.scopeFor(file)?.let {
                    language.setCompleterKeywords(completionWords(it))
                }
                is PlainTextLanguage -> language.setCompleterKeywords(userWords.words())
            }
        }
    }

    companion object {
        const val LIGHT_THEME = "xcode-light"
        const val DARK_THEME = "xcode-dark"
    }
}
