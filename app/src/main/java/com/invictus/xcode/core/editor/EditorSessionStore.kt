package com.invictus.xcode.core.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class SessionTab(
    val path: String,
    val name: String,
    val isPinned: Boolean,
    val cursorLine: Int,
    val cursorColumn: Int,
    val scrollX: Int,
    val scrollY: Int,
    /** Hash of the file's content as far as this app instance knows it (last read or write). */
    val contentHash: String,
    /** Only set while the tab was dirty: the exact unsaved text. Null once saved. */
    val pendingEditSnapshot: String?,
)

data class SessionState(
    val projectPath: String,
    val activePath: String?,
    val tabs: List<SessionTab>,
)

/**
 * Session restore (M3 part 3, plan section 3.2): one small JSON file per project under
 * `filesDir/editor_sessions/`, keyed by a hash of the project path -- not Room, this is a
 * single blob read/written whole, matching `EditorStatePersistence` from the plan.
 *
 * [SessionTab.contentHash] guards restore against replaying stale edits onto a file that
 * changed on disk while the app was dead (Termux, git, another app, etc.): the caller only
 * trusts [SessionTab.pendingEditSnapshot] when the file's current on-disk hash still matches
 * the hash captured when that snapshot was taken.
 */
class EditorSessionStore(context: Context) {
    private val dir = File(context.filesDir, "editor_sessions").apply { mkdirs() }

    private fun fileFor(projectPath: String): File = File(dir, "${sha256(projectPath)}.json")

    /** Blocking; call from Dispatchers.IO. */
    fun load(projectPath: String): SessionState? {
        val file = fileFor(projectPath)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val tabsJson = root.getJSONArray("tabs")
            val tabs = buildList {
                for (i in 0 until tabsJson.length()) {
                    val t = tabsJson.getJSONObject(i)
                    add(
                        SessionTab(
                            path = t.getString("path"),
                            name = t.getString("name"),
                            isPinned = t.optBoolean("isPinned", false),
                            cursorLine = t.optInt("cursorLine", 0),
                            cursorColumn = t.optInt("cursorColumn", 0),
                            scrollX = t.optInt("scrollX", 0),
                            scrollY = t.optInt("scrollY", 0),
                            contentHash = t.optString("contentHash", ""),
                            pendingEditSnapshot = t.optString("pendingEditSnapshot", null)
                                .takeUnless { it.isNullOrEmpty() },
                        ),
                    )
                }
            }
            SessionState(
                projectPath = root.getString("projectPath"),
                activePath = root.optString("activePath", null).takeUnless { it.isNullOrEmpty() },
                tabs = tabs,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Blocking; call from Dispatchers.IO. */
    fun save(state: SessionState) {
        try {
            val tabsJson = JSONArray()
            state.tabs.forEach { tab ->
                tabsJson.put(
                    JSONObject()
                        .put("path", tab.path)
                        .put("name", tab.name)
                        .put("isPinned", tab.isPinned)
                        .put("cursorLine", tab.cursorLine)
                        .put("cursorColumn", tab.cursorColumn)
                        .put("scrollX", tab.scrollX)
                        .put("scrollY", tab.scrollY)
                        .put("contentHash", tab.contentHash)
                        .put("pendingEditSnapshot", tab.pendingEditSnapshot),
                )
            }
            val root = JSONObject()
                .put("projectPath", state.projectPath)
                .put("activePath", state.activePath)
                .put("tabs", tabsJson)
            fileFor(state.projectPath).writeText(root.toString())
        } catch (_: Exception) {
            // Best effort -- losing session restore beats crashing on a background save.
        }
    }

    /** Blocking; call from Dispatchers.IO. */
    fun clear(projectPath: String) {
        fileFor(projectPath).delete()
    }

    companion object {
        fun hashOf(text: String): String = sha256(text)

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) sb.append("%02x".format(b))
            return sb.toString()
        }
    }
}
