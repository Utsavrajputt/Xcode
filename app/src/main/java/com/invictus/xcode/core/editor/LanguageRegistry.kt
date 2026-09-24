package com.invictus.xcode.core.editor

import java.io.File
import java.util.Locale

/**
 * File name / extension -> TextMate scope. The scopes are the ones declared in
 * assets/textmate/languages.json. Anything not listed opens as plain text.
 */
object LanguageRegistry {

    private val byName = mapOf(
        "gradle.properties" to "source.properties",
        "local.properties" to "source.properties",
        "gradlew" to "source.shell",
        ".bashrc" to "source.shell",
        ".zshrc" to "source.shell",
        ".profile" to "source.shell",
        ".gitignore" to null,
    )

    private val byExtension = buildMap {
        fun map(scope: String, vararg ext: String) = ext.forEach { put(it, scope) }
        map("source.kotlin", "kt", "kts")
        map("source.java", "java")
        map("source.groovy", "gradle", "groovy")
        map("source.smali", "smali")
        map("text.xml", "xml", "svg", "xsd", "xsl", "xslt", "plist", "iml", "axml")
        map("text.html.basic", "html", "htm", "xhtml")
        map("source.json", "json", "jsonc", "json5", "webmanifest")
        map("source.yaml", "yaml", "yml")
        map("text.html.markdown", "md", "markdown", "mdx")
        map("source.css", "css")
        map("source.js", "js", "mjs", "cjs", "jsx", "ts", "tsx")
        map("source.python", "py", "pyw", "pyi")
        map("source.shell", "sh", "bash", "zsh", "ksh")
        map("source.c", "c", "h")
        map("source.cpp", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "inl")
        map("source.dart", "dart")
        map("source.toml", "toml")
        map("source.properties", "properties", "prop", "conf", "ini", "cfg")
    }

    fun scopeFor(file: File): String? {
        val name = file.name.lowercase(Locale.ROOT)
        if (name in byName) return byName[name]
        return byExtension[file.extension.lowercase(Locale.ROOT)]
    }
}
