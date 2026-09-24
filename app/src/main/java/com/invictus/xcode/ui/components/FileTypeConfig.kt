package com.invictus.xcode.ui.components

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** How a file shows up in the tree: a short label on a coloured badge. */
data class FileTypeStyle(
    val label: String,
    val background: Color,
    val foreground: Color = Color.White,
)

/** Extension (and a few well-known names) to badge style. Unknown types get a neutral badge. */
object FileTypeConfig {

    private val default = FileTypeStyle("\u2261", Color(0xFF78909C))

    private val byName: Map<String, FileTypeStyle> = mapOf(
        ".gitignore" to FileTypeStyle("GIT", Color(0xFFF05032)),
        ".gitattributes" to FileTypeStyle("GIT", Color(0xFFF05032)),
        ".gitmodules" to FileTypeStyle("GIT", Color(0xFFF05032)),
    )

    private val byExtension: Map<String, FileTypeStyle> = buildMap {
        fun add(style: FileTypeStyle, vararg extensions: String) = extensions.forEach { put(it, style) }

        add(FileTypeStyle("KT", Color(0xFF7F52FF)), "kt")
        add(FileTypeStyle("KTS", Color(0xFF7F52FF)), "kts")
        add(FileTypeStyle("JV", Color(0xFFE76F00)), "java")
        add(FileTypeStyle("XML", Color(0xFF3DDC84), Color.Black), "xml")
        add(FileTypeStyle("{}", Color(0xFFF9A825), Color.Black), "json", "json5")
        add(FileTypeStyle("MD", Color(0xFF546E7A)), "md", "markdown")
        add(FileTypeStyle("SML", Color(0xFF795548)), "smali")
        add(FileTypeStyle("GRD", Color(0xFF00796B)), "gradle")
        add(FileTypeStyle("YML", Color(0xFFC62828)), "yml", "yaml")
        add(FileTypeStyle("HTM", Color(0xFFE44D26)), "html", "htm")
        add(FileTypeStyle("CSS", Color(0xFF1572B6)), "css")
        add(FileTypeStyle("JS", Color(0xFFF7DF1E), Color.Black), "js", "mjs", "cjs")
        add(FileTypeStyle("TS", Color(0xFF3178C6)), "ts")
        add(FileTypeStyle("PY", Color(0xFF3776AB)), "py")
        add(FileTypeStyle("SH", Color(0xFF455A64)), "sh", "bash", "zsh")
        add(FileTypeStyle("C", Color(0xFF00599C)), "c")
        add(FileTypeStyle("H", Color(0xFF7B1FA2)), "h")
        add(FileTypeStyle("C++", Color(0xFF00599C)), "cpp", "cc", "cxx")
        add(FileTypeStyle("H++", Color(0xFF7B1FA2)), "hpp", "hh")
        add(FileTypeStyle("DRT", Color(0xFF0175C2)), "dart")
        add(FileTypeStyle("TML", Color(0xFF9C4221)), "toml")
        add(FileTypeStyle("PRP", Color(0xFF6D4C41)), "properties")
        add(FileTypeStyle("TXT", Color(0xFF757575)), "txt", "log")
        add(FileTypeStyle("IMG", Color(0xFF8E24AA)), "png", "jpg", "jpeg", "webp", "gif", "bmp")
        add(FileTypeStyle("SVG", Color(0xFFFF9800), Color.Black), "svg")
        add(FileTypeStyle("VID", Color(0xFFD81B60)), "mp4", "mkv", "webm", "3gp", "mov")
        add(FileTypeStyle("APK", Color(0xFF3DDC84), Color.Black), "apk", "aab")
        add(FileTypeStyle("ZIP", Color(0xFF6D6D6D)), "zip", "jar", "aar", "7z", "rar", "gz", "tar", "tgz")
    }

    fun styleFor(fileName: String): FileTypeStyle {
        val lower = fileName.lowercase(Locale.ROOT)
        byName[lower]?.let { return it }
        val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (ext.isEmpty()) return default
        return byExtension[ext] ?: FileTypeStyle(ext.uppercase(Locale.ROOT).take(3), default.background)
    }
}
