package com.invictus.kodex.core.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remembers words the user has actually typed (project names, GitHub usernames, URL segments...)
 * so they are offered by autocomplete in every file and project, not just where they were typed.
 *
 * Learning happens on save: only tokens that are new compared with the file's previous content
 * count, so renaming `mpvrx` to `Replay` teaches `Replay` (and never re-teaches old text).
 * A URL such as `github.com/name/Replay` contributes `github`, `name` and `Replay`, because the
 * editor completes one identifier-like piece at a time.
 */
class UserWordStore(context: Context) {

    private class Entry(var count: Int, var lastSeen: Long)

    private val file = File(context.applicationContext.filesDir, "user_words.json")
    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private var cached: List<String> = emptyList()

    /** Bumped whenever [words] changes, so open editors know to refresh their completion list. */
    @Volatile
    var version: Int = 0
        private set

    init {
        load()
        cached = rank()
    }

    /** Most useful first: recently and frequently typed. Cheap; returns a cached snapshot. */
    fun words(): List<String> = synchronized(lock) { cached }

    /** All completion-worthy tokens in [text]. */
    fun tokens(text: String): Set<String> {
        val out = HashSet<String>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (isStart(text[i])) {
                val start = i
                i++
                while (i < n && (Character.isLetterOrDigit(text[i]) || text[i] == '_')) i++
                val len = i - start
                if (len in MIN_LEN..MAX_LEN) out.add(text.substring(start, i))
            } else {
                i++
            }
        }
        return out
    }

    /**
     * Learns tokens of [text] that are absent from [baseline] (the file's previous tokens; null
     * means "don't know, learn nothing"). Returns the token set to use as the next baseline.
     * Blocking disk write: call from Dispatchers.IO.
     */
    fun learn(baseline: Set<String>?, text: String): Set<String> {
        val now = tokens(text)
        if (baseline == null) return now
        val fresh = now.filterNot { it in baseline }
        if (fresh.isEmpty()) return now
        synchronized(lock) {
            val stamp = System.currentTimeMillis()
            for (word in fresh) {
                val entry = entries.getOrPut(word) { Entry(0, stamp) }
                entry.count++
                entry.lastSeen = stamp
            }
            if (entries.size > MAX_WORDS) trim()
            cached = rank()
            version++
            save()
        }
        return now
    }

    private fun isStart(c: Char) = Character.isLetter(c) || c == '_'

    private fun rank(): List<String> =
        entries.entries
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.lastSeen }.thenByDescending { it.value.count })
            .map { it.key }

    private fun trim() {
        val keep = entries.entries
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.lastSeen }.thenByDescending { it.value.count })
            .take(MAX_WORDS * 3 / 4)
            .map { it.key }
            .toSet()
        entries.keys.retainAll(keep)
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries[o.getString("w")] = Entry(o.optInt("c", 1), o.optLong("t", 0L))
            }
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            entries.forEach { (w, e) ->
                arr.put(JSONObject().put("w", w).put("c", e.count).put("t", e.lastSeen))
            }
            file.writeText(arr.toString())
        }
    }

    private companion object {
        const val MIN_LEN = 3
        const val MAX_LEN = 40
        const val MAX_WORDS = 2000
    }
}
