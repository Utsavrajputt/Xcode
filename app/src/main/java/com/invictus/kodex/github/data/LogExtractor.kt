package com.invictus.kodex.github.data

/**
 * Pure functions over raw GitHub Actions job-log text (plan 8.1): strip timestamps/ANSI and cut out
 * the part that explains the failure.
 */
object LogExtractor {
    const val MAX_LINES = 200
    const val MAX_BYTES = 20 * 1024
    const val CONTEXT_BEFORE = 15
    const val FALLBACK_TAIL = 150
    const val TRUNCATED_NOTE = "… (truncated; use \"Copy full log\")"

    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val TIMESTAMP = Regex("^\\uFEFF?\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z ?")

    /** Ordered by how specific they are; the earliest hit inside the failing step wins. */
    private val ANCHORS = listOf(
        "##[error]", "FAILURE: Build failed", "* What went wrong:", "e: file://", "error:",
        "Exception in thread", "Caused by:", "Execution failed for task", "AAPT:", "ERROR:",
    )

    data class Header(
        val repo: String,
        val workflow: String,
        val runNumber: Int,
        val branch: String,
        val sha7: String,
        val failedStep: String?,
    ) {
        fun render(): String = buildString {
            append(repo).append(" • ").append(workflow).append(" • run #").append(runNumber)
                .append(" • ").append(branch).append(" • ").append(sha7)
            if (!failedStep.isNullOrBlank()) append(" • ").append(failedStep)
        }
    }

    /** Removes ANSI escapes and the leading ISO timestamp from each line. */
    fun clean(raw: String): List<String> =
        raw.lineSequence().map { TIMESTAMP.replace(ANSI.replace(it, ""), "").trimEnd('\r') }.toList()
            .let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }

    /** Error excerpt for a cleaned job log, with a one-line [header] on top. */
    fun excerpt(cleanLines: List<String>, header: Header): String {
        val body = bodyLines(cleanLines)
        val limited = limit(body)
        return buildString {
            append(header.render()).append('\n')
            append(limited.text)
            if (limited.truncated) append('\n').append(TRUNCATED_NOTE)
        }
    }

    private class Limited(val text: String, val truncated: Boolean)

    private fun bodyLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val lastError = lines.indexOfLast { it.startsWith("##[error]") }
        if (lastError >= 0) {
            // The failing step is the last "##[group]Run" before its final ##[error]; cleanup steps follow it.
            val segStart = (0..lastError).lastOrNull { lines[it].startsWith("##[group]Run") } ?: 0
            val anchor = (segStart..lastError).firstOrNull { i -> ANCHORS.any { lines[i].contains(it) } } ?: lastError
            val from = maxOf(segStart, anchor - CONTEXT_BEFORE)
            // Include a couple of trailing lines, but never the next step's "##[group]" / cleanup output.
            var to = lastError + 1
            while (to < lines.size && to < lastError + 3 && !lines[to].startsWith("##[")) to++
            return lines.subList(from, to)
        }
        val anchor = lines.indexOfFirst { l -> ANCHORS.any { l.contains(it) } }
        if (anchor >= 0) return lines.subList(maxOf(0, anchor - CONTEXT_BEFORE), lines.size)
        return lines.takeLast(FALLBACK_TAIL)
    }

    private fun limit(lines: List<String>): Limited {
        var truncated = false
        var picked = lines
        if (picked.size > MAX_LINES) { picked = picked.take(MAX_LINES); truncated = true }
        val sb = StringBuilder()
        for ((i, l) in picked.withIndex()) {
            if (sb.length + l.length + 1 > MAX_BYTES) { truncated = true; break }
            if (i > 0) sb.append('\n')
            sb.append(l)
        }
        return Limited(sb.toString(), truncated)
    }

    /** Header-only text for a run that failed before any job existed (e.g. a workflow-file error). */
    fun noJobsText(header: Header, displayTitle: String, conclusion: String?): String =
        header.render() + "\n" + displayTitle + (conclusion?.let { "\nConclusion: $it" } ?: "") +
            "\nNo job logs: the run failed before any job started."
}
