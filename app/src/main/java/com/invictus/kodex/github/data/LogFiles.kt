package com.invictus.kodex.github.data

import java.io.File
import java.io.RandomAccessFile

/** Disk-backed job logs: the file holds the whole log, memory only ever sees a bounded tail. */
object LogFiles {
    /** Hard cap for what is streamed to disk. */
    const val MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024

    /** How much of the end of the log is shown / analysed (plan 6.3: "5 MB shown"). */
    const val VIEW_TAIL_BYTES = 5L * 1024 * 1024

    /** Clipboard transactions fail above ~1 MB, so "Copy full log" is capped just below that. */
    const val CLIPBOARD_MAX_CHARS = 800_000

    data class Tail(val lines: List<String>, val cutAtStart: Boolean)

    /** Last [maxBytes] of [file] as cleaned lines (ANSI + timestamps stripped). */
    fun readTail(file: File, maxBytes: Long = VIEW_TAIL_BYTES): Tail {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val start = (len - maxBytes).coerceAtLeast(0)
            raf.seek(start)
            val bytes = ByteArray((len - start).toInt())
            raf.readFully(bytes)
            var text = String(bytes, Charsets.UTF_8)
            val cut = start > 0
            if (cut) text = text.substringAfter('\n', "") // drop the (probably partial) first line
            return Tail(LogExtractor.clean(text), cut)
        }
    }

    /** Text for the clipboard: the cleaned tail, itself cut to [CLIPBOARD_MAX_CHARS]. Second = was cut. */
    fun clipboardText(file: File): Pair<String, Boolean> {
        val tail = readTail(file)
        val joined = tail.lines.joinToString("\n")
        return if (joined.length > CLIPBOARD_MAX_CHARS) {
            joined.takeLast(CLIPBOARD_MAX_CHARS) to true
        } else {
            joined to (tail.cutAtStart || file.length() > VIEW_TAIL_BYTES)
        }
    }

    fun cleanupDir(dir: File, olderThanMs: Long = 60 * 60 * 1000L, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { if (now - it.lastModified() > olderThanMs) it.delete() }
    }
}
