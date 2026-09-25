package com.invictus.xcode.core.editor

/** One selectable TextMate colour scheme. [assetName] matches `assets/textmate/<assetName>.json`. */
data class EditorTheme(val id: String, val displayName: String, val assetName: String, val isDark: Boolean)

/**
 * Plan section 3.3 "Editor color themes": default light/dark (follows the app theme) plus the
 * bundled TextMate theme picks -- Darcula, Eclipse, Ayu, Monokai, Solarized and GitHub.
 */
object EditorThemes {
    /** Not a real theme id: means "follow the app's own light/dark", the pre-M3-part-3 behavior. */
    const val SYSTEM_DEFAULT = "system"

    val ALL: List<EditorTheme> = listOf(
        EditorTheme("darcula", "Darcula", "darcula", isDark = true),
        EditorTheme("eclipse", "Eclipse", "eclipse", isDark = false),
        EditorTheme("ayu-dark", "Ayu Dark", "ayu-dark", isDark = true),
        EditorTheme("monokai", "Monokai", "monokai", isDark = true),
        EditorTheme("solarized-light", "Solarized Light", "solarized-light", isDark = false),
        EditorTheme("solarized-dark", "Solarized Dark", "solarized-dark", isDark = true),
        EditorTheme("github-light", "GitHub Light", "github-light", isDark = false),
        EditorTheme("github-dark", "GitHub Dark", "github-dark", isDark = true),
    )

    fun find(id: String): EditorTheme? = ALL.firstOrNull { it.id == id }
}
