package com.invictus.xcode.core.search

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Open Project sheet's folder search: naam se fuzzy match, poore [root] ko recursively scan
 * karta hai (chahe user kisi bhi shortcut/subfolder me browse kar raha ho) — live scan, caller
 * debounce+cancel karta hai, jaise [FileSearchEngine].
 */
class FolderSearchEngine {
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
    ): List<Result> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val rootPath = root.canonicalPath
        val results = ArrayList<Result>()

        fun walk(dir: File) {
            coroutineContext.ensureActive() // naya query aaya -> purana scan cancel
            if (results.size >= MAX_RESULTS) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                coroutineContext.ensureActive()
                if (!child.isDirectory) continue
                val name = child.name
                if (!showHidden && name.startsWith(".")) continue
                if (name in SKIPPED_DIRS) continue
                // Android/data aur Android/obb: scoped-storage se ache se list nahi hote,
                // aur heavy hote hain — sirf inhi do ko skip karo, baaki Android/ ke andar allowed.
                if (dir.name == "Android" && name in ANDROID_SKIPPED) continue
                val m = FuzzyMatcher.match(query, name)
                if (m != null) {
                    val rel = child.canonicalPath.removePrefix(rootPath).trim('/', ' ')
                    results.add(Result(child, name, rel, m.score, m.indices))
                }
                walk(child)
            }
        }
        walk(root)
        results.sortByDescending { it.score }
        results.take(MAX_RESULTS)
    }

    companion object {
        private val SKIPPED_DIRS = setOf(".git", "build", "node_modules", ".gradle", ".idea")
        private val ANDROID_SKIPPED = setOf("data", "obb")
        const val MAX_RESULTS = 200
    }
}
