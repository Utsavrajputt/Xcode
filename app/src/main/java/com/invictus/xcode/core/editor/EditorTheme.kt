package com.invictus.xcode.core.editor

/**
 * One selectable TextMate colour scheme. [assetName] matches `assets/textmate/<assetName>.json`.
 *
 * [preview] carries a handful of representative token colours (background, comment, string,
 * keyword, function-name) pulled from that same JSON, used to draw a mini code mock-up in the
 * theme picker without having to parse the TextMate JSON at UI time.
 */
data class EditorTheme(
    val id: String,
    val displayName: String,
    val assetName: String,
    val isDark: Boolean,
    val preview: EditorThemePreviewColors,
)

/** Representative syntax colours for a theme, used to render its picker preview card. */
data class EditorThemePreviewColors(
    val background: Long,
    val foreground: Long,
    val comment: Long,
    val string: Long,
    val keyword: Long,
    val function: Long,
)

/**
 * Plan section 3.3 "Editor color themes": default light/dark (follows the app theme) plus the
 * bundled TextMate theme picks -- Darcula, Eclipse, Ayu, Monokai, Solarized, GitHub and VS Code.
 */
object EditorThemes {
    /** Not a real theme id: means "follow the app's own light/dark", the pre-M3-part-3 behavior. */
    const val SYSTEM_DEFAULT = "system"

    val ALL: List<EditorTheme> = listOf(
        EditorTheme(
            "darcula", "Darcula", "darcula", isDark = true,
            preview = EditorThemePreviewColors(0xFF2B2B2B, 0xFFA9B7C6, 0xFF808080, 0xFF6A8759, 0xFFCC7832, 0xFFFFC66D),
        ),
        EditorTheme(
            "eclipse", "Eclipse", "eclipse", isDark = false,
            preview = EditorThemePreviewColors(0xFFFFFFFF, 0xFF000000, 0xFF3F7F5F, 0xFF2A00FF, 0xFF7F0055, 0xFF000000),
        ),
        EditorTheme(
            "ayu-dark", "Ayu Dark", "ayu-dark", isDark = true,
            preview = EditorThemePreviewColors(0xFF0A0E14, 0xFFB3B1AD, 0xFF5C6773, 0xFFC2D94C, 0xFFFF8F40, 0xFFFFB454),
        ),
        EditorTheme(
            "monokai", "Monokai", "monokai", isDark = true,
            preview = EditorThemePreviewColors(0xFF272822, 0xFFF8F8F2, 0xFF75715E, 0xFFE6DB74, 0xFFF92672, 0xFFA6E22E),
        ),
        EditorTheme(
            "solarized-light", "Solarized Light", "solarized-light", isDark = false,
            preview = EditorThemePreviewColors(0xFFFDF6E3, 0xFF657B83, 0xFF93A1A1, 0xFF2AA198, 0xFF859900, 0xFF268BD2),
        ),
        EditorTheme(
            "solarized-dark", "Solarized Dark", "solarized-dark", isDark = true,
            preview = EditorThemePreviewColors(0xFF002B36, 0xFF839496, 0xFF586E75, 0xFF2AA198, 0xFF859900, 0xFF268BD2),
        ),
        EditorTheme(
            "github-light", "GitHub Light", "github-light", isDark = false,
            preview = EditorThemePreviewColors(0xFFFFFFFF, 0xFF24292E, 0xFF6A737D, 0xFF032F62, 0xFFD73A49, 0xFF6F42C1),
        ),
        EditorTheme(
            "github-dark", "GitHub Dark", "github-dark", isDark = true,
            preview = EditorThemePreviewColors(0xFF0D1117, 0xFFC9D1D9, 0xFF8B949E, 0xFFA5D6FF, 0xFFFF7B72, 0xFFD2A8FF),
        ),
        EditorTheme(
            "vscode-dark", "VS Code Dark+", "vscode-dark", isDark = true,
            preview = EditorThemePreviewColors(0xFF1E1E1E, 0xFFD4D4D4, 0xFF6A9955, 0xFFCE9178, 0xFF569CD6, 0xFFDCDCAA),
        ),
        EditorTheme(
            "vscode-light", "VS Code Light+", "vscode-light", isDark = false,
            preview = EditorThemePreviewColors(0xFFFFFFFF, 0xFF000000, 0xFF008000, 0xFFA31515, 0xFF0000FF, 0xFF795E26),
        ),
    )

    fun find(id: String): EditorTheme? = ALL.firstOrNull { it.id == id }
}
