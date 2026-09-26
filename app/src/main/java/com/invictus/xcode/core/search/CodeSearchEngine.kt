package com.invictus.xcode.core.search

import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.coroutineContext

/** Plan 3.6 code search: poora open workspace, plain/regex, globs, results Flow se stream. */
class CodeSearchEngine {
    data class Options(
        val regex: Boolean = false,
        val caseSensitive: Boolean = false,
        val wholeWord: Boolean = false,
        val includeGlob: String = "",
        val excludeGlob: String = "",
        val maxFileSizeBytes: Long = 2L * 1024 * 1024,
    )

    data class LineMatch(val line: Int, val text: String, val ranges: List<IntRange>)
    data class FileResult(val file: File, val relativePath: String, val matches: List<LineMatch>)

    fun search(root: File, query: String, options: Options): Flow<FileResult> = flow {
        if (query.isBlank()) return@flow
        val pattern = buildPattern(query, options)
        val include = options.includeGlob.takeIf { it.isNotBlank() }?.let { globToRegex(it) }
        val userExcludes = options.excludeGlob
            .split(',', '\n').map { it.trim().removePrefix("!") }.filter { it.isNotEmpty() }
        val excludes = (DEFAULT_EXCLUDES + userExcludes).map { globToRegex(it) }
        val rootPath = root.canonicalPath
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                coroutineContext.ensureActive()
                val name = child.name
                if (name.startsWith(".")) continue // .git waghera hamesha skip
                if (child.isDirectory) {
                    if (name in DEFAULT_SKIPPED_DIRS) continue
                    stack.add(child)
                    continue
                }
                val rel = child.canonicalPath.removePrefix(rootPath)
                if (include != null && !include.matches(rel)) continue
                if (excludes.any { it.matches(rel) }) continue
                if (child.length() > options.maxFileSizeBytes || isBinary(child)) continue
                val matches = scan(child, pattern)
                if (matches.isNotEmpty()) emit(FileResult(child, rel, matches))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPattern(query: String, options: Options): Pattern {
        var src = if (options.regex) query else Pattern.quote(query)
        if (options.wholeWord) src = "\\b(?:$src)\\b"
        val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(src, flags)
    }

    /** Pehle 8KB me null byte -> binary; text search me skip. */
    private fun isBinary(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            val n = input.read(buf)
            if (n < 0) return@runCatching false
            for (i in 0 until n) {
                if (buf[i].toInt() == 0) return@runCatching true
            }
            false
        }
    }.getOrDefault(true)

    private fun scan(file: File, pattern: Pattern): List<LineMatch> {
        val out = ArrayList<LineMatch>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var lineNo = 0
            while (out.size < MAX_MATCHES_PER_FILE) {
                val line = reader.readLine() ?: break
                lineNo++
                val matcher = pattern.matcher(line)
                val ranges = ArrayList<IntRange>(8)
                while (matcher.find()) {
                    ranges.add(matcher.start() until matcher.end())
                    if (ranges.size >= 8) break
                }
                if (ranges.isNotEmpty()) out.add(LineMatch(lineNo, line.take(500), ranges))
            }
        }
        return out
    }

    /** Glob -> regex: `*` = ek path segment, `**` = kuch bhi, `?` = single char. */
    fun globToRegex(glob: String): Regex {
        val s = glob.trim().replace('\\', '/')
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            when (val ch = s[i]) {
                '*' -> if (i + 1 < s.length && s[i + 1] == '*') {
                    sb.append(".*")
                    i++
                } else {
                    sb.append("[^/]*")
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '^', '$', '|', '[', ']', '{', '}' -> {
                    sb.append('\\')
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
            i++
        }
        return Regex("(?i).*(?:^|/)" + sb + "$")
    }

    companion object {
        val DEFAULT_SKIPPED_DIRS = setOf(".git", "build", "node_modules", ".gradle")
        val DEFAULT_EXCLUDES = listOf(
            ".git/**", "build/**", "node_modules/**",
            "*.apk", "*.zip", "*.jar", "*.aar", "*.png", "*.jpg", "*.jpeg", "*.webp",
            "*.gif", "*.class", "*.dex", "*.so", "*.ttf", "*.otf", "*.woff", "*.woff2",
        )
        const val MAX_MATCHES_PER_FILE = 200
    }
}
