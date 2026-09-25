package com.invictus.xcode.core.editor

import java.io.File
import java.util.Locale

/**
 * Plan 3.2 quick action "comment toggle": one line-comment prefix per language, keyed the
 * same way as [LanguageRegistry]. Languages with no single-line comment (JSON, XML/HTML,
 * which only support block comments) are left out on purpose -- the action stays unavailable
 * there instead of inserting something that isn't actually a comment.
 */
object CommentSyntax {
    private val hashCommentExtensions = setOf(
        "py", "pyw", "pyi", "sh", "bash", "zsh", "ksh",
        "yaml", "yml", "toml", "properties", "prop", "conf", "ini", "cfg",
    )
    private val slashCommentExtensions = setOf(
        "kt", "kts", "java", "gradle", "groovy", "js", "mjs", "cjs", "jsx", "ts", "tsx",
        "dart", "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "inl",
    )

    /** Null means no single-line comment syntax for this file, so the action stays hidden. */
    fun forFile(file: File): String? =
        when (file.extension.lowercase(Locale.ROOT)) {
            in hashCommentExtensions -> "# "
            in slashCommentExtensions -> "// "
            else -> null
        }
}
