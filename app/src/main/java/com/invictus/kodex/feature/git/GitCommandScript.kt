package com.invictus.kodex.feature.git

/**
 * A pasted "git add / git commit -m / git push" snippet, reduced to what the app should do.
 * [stageAll] only when `git add` was present, [push] only when `git push` was present.
 */
internal data class GitCommandScript(val message: String, val stageAll: Boolean, val push: Boolean)

private val COMMIT_RE = Regex(
    """\bgit\s+commit\b[^\n]*?\s-[A-Za-z]*m\s*(?:"((?:[^"\\]|\\.)*)"|'([^']*)'|(\S+))""",
)
private val ADD_RE = Regex("""\bgit\s+add\b""")
private val PUSH_RE = Regex("""\bgit\s+push\b""")
private val AMEND_RE = Regex("""--amend\b""")
private val ESCAPE_RE = Regex("""\\([\\"$`])""")

/** Returns null unless [text] contains a `git commit -m "..."` with a non-blank message. */
internal fun parseGitScript(text: String): GitCommandScript? {
    val m = COMMIT_RE.find(text) ?: return null
    if (AMEND_RE.containsMatchIn(m.value)) return null
    val raw = m.groups[1]?.value?.let { ESCAPE_RE.replace(it) { e -> e.groupValues[1] } }
        ?: m.groups[2]?.value
        ?: m.groups[3]?.value
        ?: return null
    val message = raw.trim()
    if (message.isEmpty()) return null
    return GitCommandScript(
        message = message,
        stageAll = ADD_RE.containsMatchIn(text),
        push = PUSH_RE.containsMatchIn(text),
    )
}
