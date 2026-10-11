package com.invictus.kodex.github

import com.invictus.kodex.github.data.LogExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExtractorTest {

    private val header = LogExtractor.Header("o/r", "Android build", 12, "main", "0123456", "Build unsigned release APKs")

    private fun ts(line: String) = "2026-10-09T10:00:00.1234567Z $line"

    @Test fun clean_strips_timestamps_and_ansi() {
        val lines = LogExtractor.clean(ts("\u001B[31;1merror:\u001B[0m boom") + "\r\n" + ts("plain") + "\n")
        assertEquals(listOf("error: boom", "plain"), lines)
    }

    @Test fun clean_strips_bom_timestamp_on_first_line() {
        assertEquals(listOf("hello"), LogExtractor.clean("\uFEFF2026-10-09T10:00:00.0000000Z hello"))
    }

    private fun gradleLog(): List<String> = LogExtractor.clean(
        buildString {
            appendLine(ts("##[group]Run actions/checkout@v5"))
            appendLine(ts("error: this is a harmless line from an earlier, successful step"))
            appendLine(ts("##[endgroup]"))
            appendLine(ts("##[group]Run ./gradlew assembleRelease"))
            repeat(30) { appendLine(ts("> Task :app:step$it")) }
            appendLine(ts("e: file:///work/App.kt:10:5 Unresolved reference: foo"))
            appendLine(ts("> Task :app:compileReleaseKotlin FAILED"))
            appendLine(ts("FAILURE: Build failed with an exception."))
            appendLine(ts("* What went wrong:"))
            appendLine(ts("Execution failed for task ':app:compileReleaseKotlin'."))
            appendLine(ts("##[error]Process completed with exit code 1."))
            appendLine(ts("##[group]Run actions/upload-artifact@v4"))
            appendLine(ts("Caused by: unrelated cleanup noise"))
            appendLine(ts("##[endgroup]"))
        },
    )

    @Test fun excerpt_targets_failing_step_not_earlier_noise() {
        val out = LogExtractor.excerpt(gradleLog(), header)
        assertTrue(out.startsWith(header.render() + "\n"))
        assertTrue(out.contains("e: file:///work/App.kt:10:5 Unresolved reference: foo"))
        assertTrue(out.contains("##[error]Process completed with exit code 1."))
        assertFalse("earlier step must not leak in", out.contains("harmless line"))
        assertFalse("post-failure cleanup must not leak in", out.contains("unrelated cleanup noise"))
    }

    @Test fun excerpt_includes_up_to_15_lines_of_context_before_first_anchor() {
        val out = LogExtractor.excerpt(gradleLog(), header).lines()
        val anchor = out.indexOfFirst { it.startsWith("e: file://") }
        // header line + body; body begins with the group line or 15 lines before the anchor.
        assertTrue(anchor - 1 <= LogExtractor.CONTEXT_BEFORE)
        assertTrue(out.any { it.startsWith("> Task :app:step") })
    }

    @Test fun excerpt_is_capped_to_200_lines_with_note() {
        val big = LogExtractor.clean(
            buildString {
                appendLine("##[group]Run ./gradlew build")
                repeat(500) { appendLine("error: problem $it") }
                appendLine("##[error]Process completed with exit code 1.")
            },
        )
        val out = LogExtractor.excerpt(big, header)
        assertTrue(out.endsWith(LogExtractor.TRUNCATED_NOTE))
        // header + 200 body lines + note
        assertEquals(LogExtractor.MAX_LINES + 2, out.lines().size)
    }

    @Test fun excerpt_is_capped_to_20kb() {
        val wide = LogExtractor.clean(
            buildString {
                appendLine("##[group]Run x")
                repeat(100) { appendLine("error: " + "x".repeat(500)) }
                appendLine("##[error]Process completed with exit code 1.")
            },
        )
        val out = LogExtractor.excerpt(wide, header)
        assertTrue(out.length <= LogExtractor.MAX_BYTES + header.render().length + LogExtractor.TRUNCATED_NOTE.length + 4)
        assertTrue(out.endsWith(LogExtractor.TRUNCATED_NOTE))
    }

    @Test fun no_error_marker_falls_back_to_first_anchor() {
        val lines = LogExtractor.clean("a\nb\nException in thread \"main\" java.lang.IllegalStateException\nat x\n")
        val out = LogExtractor.excerpt(lines, header)
        assertTrue(out.contains("Exception in thread"))
    }

    @Test fun no_anchor_falls_back_to_last_150_lines() {
        val lines = LogExtractor.clean((1..400).joinToString("\n") { "line $it" })
        val body = LogExtractor.excerpt(lines, header).lines().drop(1)
        assertEquals(LogExtractor.FALLBACK_TAIL, body.size)
        assertEquals("line 400", body.last())
        assertEquals("line 251", body.first())
    }

    @Test fun empty_log_gives_header_only() {
        val out = LogExtractor.excerpt(emptyList(), header)
        assertEquals(header.render() + "\n", out)
    }

    @Test fun jgit_failure_is_found() {
        val lines = LogExtractor.clean(
            "##[group]Run ./gradlew test\nCaused by: org.eclipse.jgit.errors.TransportException: boom\n##[error]Process completed with exit code 1.\n",
        )
        assertTrue(LogExtractor.excerpt(lines, header).contains("TransportException"))
    }

    @Test fun run_without_jobs_text_mentions_title_and_conclusion() {
        val t = LogExtractor.noJobsText(header, "Invalid workflow file", "failure")
        assertTrue(t.contains("Invalid workflow file"))
        assertTrue(t.contains("Conclusion: failure"))
        assertTrue(t.contains("before any job started"))
    }

    @Test fun header_omits_failed_step_when_unknown() {
        assertEquals("o/r • Android build • run #12 • main • 0123456", header.copy(failedStep = null).render())
    }
}
