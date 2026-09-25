package com.invictus.xcode.core.editor

/**
 * Static keyword lists for autocomplete, keyed by the same TextMate scope strings
 * [LanguageRegistry] resolves a file to. No LSP -- this is just the reserved-word list Sora's
 * [io.github.rosemoe.sora.widget.component.EditorAutoCompletion] mixes in alongside the
 * in-file identifiers TextMateLanguage already tokenizes.
 */
object LanguageKeywords {

    private val kotlin = listOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch",
        "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
        "init", "param", "property", "receiver", "set", "setparam", "where", "actual", "abstract",
        "annotation", "companion", "const", "crossinline", "data", "enum", "expect", "external",
        "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open",
        "operator", "out", "override", "private", "protected", "public", "reified", "sealed",
        "suspend", "tailrec", "vararg",
    )

    private val java = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
        "null", "var", "record", "yield", "sealed", "permits",
    )

    private val xml = listOf(
        "xmlns", "android", "app", "tools", "version", "encoding", "DOCTYPE",
    )

    private val html = listOf(
        "html", "head", "body", "div", "span", "class", "id", "style", "script", "href", "src",
        "title", "meta", "link", "table", "tr", "td", "th", "ul", "li", "ol", "form", "input",
        "button", "label", "img", "a", "p", "br", "hr", "footer", "header", "nav", "section",
    )

    private val css = listOf(
        "display", "position", "color", "background", "margin", "padding", "border", "width",
        "height", "flex", "grid", "font-size", "font-weight", "absolute", "relative", "fixed",
        "block", "inline", "none", "important", "hover", "important", "transition", "transform",
    )

    private val js = listOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "class", "extends", "super", "this", "new", "typeof",
        "instanceof", "in", "of", "try", "catch", "finally", "throw", "async", "await", "yield",
        "import", "export", "default", "from", "null", "undefined", "true", "false", "static",
        "get", "set", "interface", "type", "enum", "implements", "public", "private", "protected",
    )

    private val python = listOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield", "self",
    )

    private val shell = listOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "function", "return", "export", "local", "echo", "exit", "break", "continue", "in",
    )

    private val c = listOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
        "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch",
        "typedef", "union", "unsigned", "void", "volatile", "while",
    )

    private val cpp = c + listOf(
        "class", "namespace", "template", "public", "private", "protected", "virtual", "friend",
        "new", "delete", "try", "catch", "throw", "using", "this", "true", "false", "nullptr",
        "operator", "explicit", "override", "final", "constexpr", "auto", "decltype",
    )

    private val groovy = kotlin + listOf("def", "trait", "closure", "each", "collect", "findAll")

    private val yaml = listOf("true", "false", "null", "yes", "no")

    private val json = emptyList<String>()

    private val toml = emptyList<String>()

    private val properties = emptyList<String>()

    private val markdown = emptyList<String>()

    private val dart = listOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "covariant", "default", "deferred", "do", "dynamic", "else", "enum", "export",
        "extends", "extension", "external", "factory", "false", "final", "finally", "for", "Function",
        "get", "hide", "if", "implements", "import", "in", "interface", "is", "late", "library",
        "mixin", "new", "null", "on", "operator", "part", "required", "rethrow", "return", "set",
        "show", "static", "super", "switch", "sync", "this", "throw", "true", "try", "typedef",
        "var", "void", "while", "with", "yield",
    )

    private val bySc = mapOf(
        "source.kotlin" to kotlin,
        "source.java" to java,
        "source.groovy" to groovy,
        "text.xml" to xml,
        "text.html.basic" to html,
        "source.json" to json,
        "source.yaml" to yaml,
        "text.html.markdown" to markdown,
        "source.css" to css,
        "source.js" to js,
        "source.python" to python,
        "source.shell" to shell,
        "source.c" to c,
        "source.cpp" to cpp,
        "source.dart" to dart,
        "source.toml" to toml,
        "source.properties" to properties,
    )

    /** Keyword list for a TextMate [scope] (see [LanguageRegistry.scopeFor]), or empty if none. */
    fun forScope(scope: String?): List<String> = bySc[scope].orEmpty()
}
