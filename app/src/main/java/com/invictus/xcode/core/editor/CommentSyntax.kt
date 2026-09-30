package com.invictus.xcode.core.editor

import java.io.File
import java.util.Locale

/** How a file comments a line out: a line prefix ("// ") or a per-line wrapper ("<!-- ", " -->"). */
sealed interface CommentStyle {
    data class Line(val prefix: String) : CommentStyle
    data class Block(val open: String, val close: String) : CommentStyle
}

/**
 * Comment syntax per file type, keyed the same way as [LanguageRegistry]. Markup/stylesheet
 * languages that only have block comments (XML, HTML, SVG, CSS...) are handled by wrapping each
 * selected line in its own block comment, so toggling stays reversible line by line.
 */
object CommentSyntax {
    private val hashCommentExtensions = setOf(
        "py", "pyw", "pyi", "sh", "bash", "zsh", "ksh",
        "yaml", "yml", "toml", "properties", "prop", "conf", "ini", "cfg", "smali",
    )
    private val slashCommentExtensions = setOf(
        "kt", "kts", "java", "gradle", "groovy", "js", "mjs", "cjs", "jsx", "ts", "tsx",
        "dart", "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "inl",
    )
    private val xmlBlockExtensions = setOf(
        "xml", "html", "htm", "xhtml", "svg", "md", "markdown", "xsd", "xsl", "xslt", "plist",
    )
    private val cssBlockExtensions = setOf("css")

    /** Null means no known comment syntax for this file, so the action stays unavailable. */
    fun styleFor(file: File): CommentStyle? =
        when (file.extension.lowercase(Locale.ROOT)) {
            in hashCommentExtensions -> CommentStyle.Line("# ")
            in slashCommentExtensions -> CommentStyle.Line("// ")
            in xmlBlockExtensions -> CommentStyle.Block("<!--", "-->")
            in cssBlockExtensions -> CommentStyle.Block("/*", "*/")
            else -> null
        }
}
