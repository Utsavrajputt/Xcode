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

    /** Binary/non-text media -- never gets a text tab, always full-screen preview only. */
    SVG,
    IMAGE,
    VIDEO,
    ;

    /** MARKDOWN/HTML have real text underneath and can split; media types cannot. */
    val isMedia: Boolean get() = this == SVG || this == IMAGE || this == VIDEO
    val isTextPreview: Boolean get() = this == MARKDOWN || this == HTML
}

/** Decides [PreviewType] from a file's extension. The one place M5's routing lives. */
object PreviewRouter {

    fun typeOf(file: File): PreviewType = typeOfExtension(file.extension)

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

    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
    private val HTML_EXTENSIONS = setOf("html", "htm")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "3gp", "mov")
}
