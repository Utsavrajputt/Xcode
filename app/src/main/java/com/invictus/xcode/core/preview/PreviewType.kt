package com.invictus.xcode.core.preview

import java.io.File
import java.util.Locale

/** What kind of preview, if any, a file gets. */
enum class PreviewType {
    /** No preview: a normal text file, or an extension nothing here understands. */
    NONE,

    /** Rendered via marked.js + highlight.js inside a text tab (split/preview toggle). */
    MARKDOWN,

    /** Rendered via a sandboxed WebView serving the project root, inside a text tab. */
    HTML,

    /** Android VectorDrawable XML (`.xml` whose root is `<vector>`): converted to SVG and drawn. */
    VECTOR_XML,

    /** Binary/non-text media -- never gets a text tab, always full-screen preview only. */
    SVG,
    IMAGE,
    VIDEO,
    ;

    /** MARKDOWN/HTML/VECTOR_XML have real text underneath and can split; media types cannot. */
    val isMedia: Boolean get() = this == SVG || this == IMAGE || this == VIDEO
    val isTextPreview: Boolean get() = this == MARKDOWN || this == HTML || this == VECTOR_XML
}

/** Decides [PreviewType] from a file's extension. The one place M5's routing lives. */
object PreviewRouter {

    fun typeOf(file: File): PreviewType {
        val byExtension = typeOfExtension(file.extension)
        // .xml is everything from layouts to manifests: only a <vector> root gets a preview.
        if (byExtension == PreviewType.NONE && file.extension.equals("xml", ignoreCase = true) &&
            isVectorDrawable(file)
        ) {
            return PreviewType.VECTOR_XML
        }
        return byExtension
    }

    /** Looks only at the first 4 KB: optional `<?xml ?>` header, comments, then `<vector`. */
    private fun isVectorDrawable(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(SNIFF_BYTES)
            val read = input.read(buffer)
            read > 0 && VECTOR_ROOT.containsMatchIn(String(buffer, 0, read, Charsets.UTF_8))
        }
    }.getOrDefault(false)

    fun typeOfExtension(extension: String): PreviewType {
        val ext = extension.lowercase(Locale.ROOT)
        return when {
            ext in MARKDOWN_EXTENSIONS -> PreviewType.MARKDOWN
            ext in HTML_EXTENSIONS -> PreviewType.HTML
            ext == "svg" -> PreviewType.SVG
            ext in IMAGE_EXTENSIONS -> PreviewType.IMAGE
            ext in VIDEO_EXTENSIONS -> PreviewType.VIDEO
            else -> PreviewType.NONE
        }
    }

    private const val SNIFF_BYTES = 4096
    private val VECTOR_ROOT = Regex(
        "^\\uFEFF?\\s*(<\\?xml[^>]*\\?>\\s*)?(<!--.*?-->\\s*)*<vector[\\s>/]",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
    private val HTML_EXTENSIONS = setOf("html", "htm")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "3gp", "mov")
}
