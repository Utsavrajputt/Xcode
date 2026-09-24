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
     */
    fun applyTo(editor: CodeEditor, file: File, dark: Boolean): Boolean {
        if (!_ready.value) return false
        return try {
            ThemeRegistry.getInstance().setTheme(if (dark) DARK_THEME else LIGHT_THEME)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            val scope = LanguageRegistry.scopeFor(file)
            if (scope != null) {
                editor.setEditorLanguage(TextMateLanguage.create(scope, true))
            }
            true
        } catch (_: Exception) {
            // A bad grammar must never take the editor down with it.
            false
        }
    }

    companion object {
        const val LIGHT_THEME = "xcode-light"
        const val DARK_THEME = "xcode-dark"
    }
}
