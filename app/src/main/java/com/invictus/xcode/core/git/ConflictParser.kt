package com.invictus.xcode.core.git

/**
 * M10: parses standard 3-marker merge conflicts (<<<<<<< / ======= / >>>>>>>) out of
 * file text into render-ready blocks for the drawer and the editor sheet.
 * Pure string work, no JGit; unit-testable. diff3-style ||||||| markers are not
 * supported (plan M10): a ||||||| line is treated as ordinary content inside the
 * ours side, which keeps parsing best-effort instead of failing.
 */
object ConflictParser {

    /** One conflicted region. Line indices are 0-based, referencing the *current* text. */
    data class Block(
        val startLine: Int,   // line of '<<<<<<<'
        val sepLine: Int,     // line of '======='
        val endLine: Int,     // line of '>>>>>>>'
        val oursText: String,
        val theirsText: String,
        val labelOurs: String,
        val labelTheirs: String,
    ) {
        /** Whole region including the marker lines, for editor replacement. */
        fun replacement(choice: Choice): String = when (choice) {
            Choice.OURS -> oursText
            Choice.THEIRS -> theirsText
            Choice.BOTH -> if (oursText.isEmpty()) theirsText
                else if (theirsText.isEmpty()) oursText
                else oursText + "\n" + theirsText
        }
    }

    enum class Choice { OURS, THEIRS, BOTH }

    fun hasConflicts(text: String): Boolean =
        text.length < MAX_SCAN && text.lineSequence().any { it.marker() == "<<<<<<<" }

    fun parse(text: String): List<Block> {
        if (text.length >= MAX_SCAN) return emptyList()
        val lines = text.split('\n')
        val blocks = ArrayList<Block>()
        // A stray/unterminated "<<<<<<<" line makes the inner scan re-walk the rest of the
        // file on every outer step; this bounds total work to stay linear-ish even then.
        var budget = lines.size.toLong() * 4 + 1000
        var i = 0
        while (i < lines.size) {
            if (lines[i].marker() != "<<<<<<<") { i++; continue }
            val start = i
            var sep = -1
            var j = i + 1
            while (j < lines.size) {
                if (--budget <= 0) return blocks
                when (lines[j].marker()) {
                    "=======" -> { if (sep < 0) sep = j }
                    ">>>>>>>" -> {
                        if (sep >= 0) {
                            blocks += Block(
                                startLine = start,
                                sepLine = sep,
                                endLine = j,
                                oursText = lines.subList(start + 1, sep).joinToString("\n"),
                                theirsText = lines.subList(sep + 1, j).joinToString("\n"),
                                labelOurs = lines[start].removePrefix("<<<<<<<").trim(),
                                labelTheirs = lines[j].removePrefix(">>>>>>>").trim(),
                            )
                            i = j
                        }
                        break
                    }
                }
                j++
            }
            i++
        }
        return blocks
    }

    /** Whole-file text with [block] replaced by [replacement] (markers removed). */
    fun apply(text: String, block: Block, replacement: String): String {
        val lines = text.split('\n').toMutableList()
        if (block.endLine >= lines.size) return text
        repeat(block.endLine - block.startLine + 1) { lines.removeAt(block.startLine) }
        if (replacement.isNotEmpty()) {
            lines.addAll(block.startLine, replacement.split('\n'))
        }
        return lines.joinToString("\n")
    }

    /** Trimmed marker key, or null for ordinary content lines. Tolerates trailing \r. */
    private fun String.marker(): String? {
        val s = if (endsWith('\r')) substring(0, length - 1) else this
        return when {
            s.startsWith("<<<<<<<") -> "<<<<<<<"
            s.startsWith("=======") -> "======="
            s.startsWith(">>>>>>>") -> ">>>>>>>"
            else -> null
        }
    }

    /** Beyond this, block UI is skipped; file-level resolution in the drawer still works. */
    private const val MAX_SCAN = 512 * 1024
}
