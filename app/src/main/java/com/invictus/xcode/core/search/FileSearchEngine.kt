package com.invictus.xcode.core.search

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Plan 3.6 file search: naam se fuzzy match; live scan, caller debounce+cancel karta hai. */
class FileSearchEngine {
    data class Result(
        val file: File,
        val name: String,
        val relativePath: String,
        val score: Int,
        val matchedIndices: List<Int>,
    )

    suspend fun search(
        root: File,
        query: String,
        showHidden: Boolean = false,
        includeIgnored: Boolean = false,
        extensionFilter: String = "",
        folderFilter: String = "",
    ): List<Result> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val extensions = extensionFilter.lowercase()
            .split(',', ' ').map { it.trim().removePrefix(".") }.filter { it.isNotEmpty() }.toSet()
        val folders = folderFilter.lowercase()
            .split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        val rootPath = root.canonicalPath
        val results = ArrayList<Result>()

        fun walk(dir: File) {
            coroutineContext.ensureActive() // naya query aaya -> purana scan cancel
            val children = dir.listFiles() ?: return
            for (child in children) {
                coroutineContext.ensureActive()
                val name = child.name
                if (!showHidden && name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (!includeIgnored && name in DEFAULT_SKIPPED_DIRS) continue
                    walk(child)
                } else {
                    if (extensions.isNotEmpty() && child.extension.lowercase() !in extensions) continue
                    val m = FuzzyMatcher.match(query, name) ?: continue
                    val rel = child.canonicalPath.removePrefix(rootPath)
                    if (folders.isNotEmpty() && folders.none { rel.lowercase().contains(it) }) continue
                    results.add(Result(child, name, rel, m.score, m.indices))
                }
            }
        }
        walk(root)
        results.sortByDescending { it.score }
        results.take(MAX_RESULTS)
    }

    companion object {
        val DEFAULT_SKIPPED_DIRS = setOf(".git", "build", "node_modules", ".gradle", ".idea")
        const val MAX_RESULTS = 200
    }
}
