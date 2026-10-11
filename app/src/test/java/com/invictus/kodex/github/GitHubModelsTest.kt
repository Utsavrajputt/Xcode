package com.invictus.kodex.github

import com.invictus.kodex.github.api.GhJob
import com.invictus.kodex.github.api.GhRun
import com.invictus.kodex.github.api.GitHubError
import com.invictus.kodex.github.api.RunDisplayStatus
import com.invictus.kodex.github.api.displayStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubModelsTest {

    @Test fun status_mapping_follows_plan_table() {
        assertEquals(RunDisplayStatus.Queued, displayStatus("queued", null))
        assertEquals(RunDisplayStatus.Queued, displayStatus("waiting", null))
        assertEquals(RunDisplayStatus.Queued, displayStatus("pending", null))
        assertEquals(RunDisplayStatus.Running, displayStatus("in_progress", null))
        assertEquals(RunDisplayStatus.Success, displayStatus("completed", "success"))
        assertEquals(RunDisplayStatus.Failed, displayStatus("completed", "failure"))
        assertEquals(RunDisplayStatus.Failed, displayStatus("completed", "timed_out"))
        assertEquals(RunDisplayStatus.Failed, displayStatus("completed", "startup_failure"))
        assertEquals(RunDisplayStatus.Cancelled, displayStatus("completed", "cancelled"))
        assertEquals(RunDisplayStatus.Skipped, displayStatus("completed", "skipped"))
        assertEquals(RunDisplayStatus.Skipped, displayStatus("completed", "neutral"))
        assertEquals(RunDisplayStatus.Skipped, displayStatus("completed", "stale"))
    }

    private fun runJson(status: String, conclusion: String?) = JSONObject(
        """
        {"id": 42, "workflow_id": 7, "name": "Android build", "run_number": 12, "status": "$status",
         "conclusion": ${conclusion?.let { "\"$it\"" } ?: "null"}, "head_branch": "main",
         "head_sha": "0123456789abcdef", "event": "push", "display_title": "Fix things",
         "head_commit": {"message": "Fix things\n\nlong body"},
         "created_at": "2026-10-09T10:00:00Z", "run_started_at": "2026-10-09T10:00:05Z",
         "updated_at": "2026-10-09T10:03:05Z"}
        """.trimIndent(),
    )

    @Test fun run_parsing_handles_json_null_conclusion() {
        val run = GhRun.fromJson(runJson("in_progress", null))
        assertNull(run.conclusion) // org.json would otherwise yield the string "null"
        assertEquals(RunDisplayStatus.Running, run.display)
        assertEquals("0123456", run.shortSha)
        assertEquals("Fix things", run.commitMessage) // first line only
        assertNull(run.durationMs) // still active
        assertTrue(run.canCancel)
        assertFalse(run.canDelete)
    }

    @Test fun completed_failed_run_capabilities_and_duration() {
        val run = GhRun.fromJson(runJson("completed", "failure"))
        assertTrue(run.isFailed)
        assertTrue(run.canRerun)
        assertTrue(run.canDelete)
        assertFalse(run.canCancel)
        assertEquals(180_000L, run.durationMs)
    }

    @Test fun successful_run_cannot_be_rerun_from_menu() {
        val run = GhRun.fromJson(runJson("completed", "success"))
        assertFalse(run.canRerun)
        assertTrue(run.canDelete)
    }

    @Test fun job_parsing_finds_failed_step() {
        val job = GhJob.fromJson(
            JSONObject(
                """{"id": 5, "name": "build", "status": "completed", "conclusion": "failure",
                    "steps": [{"number":1,"name":"Checkout","conclusion":"success"},
                              {"number":2,"name":"Build unsigned release APKs","conclusion":"failure"},
                              {"number":3,"name":"Sign","conclusion":null}]}""",
            ),
        )
        assertTrue(job.isFailed)
        assertEquals("Build unsigned release APKs", job.failedStepName)
    }

    // ---- error mapping ----

    @Test fun error_401() {
        assertTrue(GitHubError.from(401, "Bad credentials") is GitHubError.Unauthorized)
    }

    @Test fun error_403_rate_limit_via_remaining() {
        val e = GitHubError.from(403, "API rate limit exceeded", remaining = 0, reset = 1_800_000_000L)
        assertTrue(e is GitHubError.RateLimited)
        assertEquals(1_800_000_000L, (e as GitHubError.RateLimited).resetEpochSec)
    }

    @Test fun error_403_secondary_limit_via_retry_after() {
        val e = GitHubError.from(403, "secondary", retryAfter = 60, nowEpochSec = 1000)
        assertEquals(1060L, (e as GitHubError.RateLimited).resetEpochSec)
    }

    @Test fun error_429_is_rate_limit() {
        assertTrue(GitHubError.from(429, null) is GitHubError.RateLimited)
    }

    @Test fun error_403_is_missing_permission_otherwise() {
        val e = GitHubError.from(403, "Resource not accessible by personal access token", remaining = 4999)
        assertTrue(e is GitHubError.MissingPermission)
    }

    @Test fun error_codes_404_409_410_422_5xx() {
        assertTrue(GitHubError.from(404, null) is GitHubError.NotFound)
        assertTrue(GitHubError.from(409, "Cannot cancel a workflow run that is not in progress.") is GitHubError.Conflict)
        assertTrue(GitHubError.from(410, null) is GitHubError.Gone)
        assertTrue(GitHubError.from(422, "Validation Failed") is GitHubError.Validation)
        assertEquals(502, (GitHubError.from(502, null) as GitHubError.Http).code)
    }

    @Test fun signed_urls_never_reach_messages() {
        val msg = GitHubError.sanitize("failed https://objects.example.com/log?sig=SECRET&x=1 done")
        assertFalse(msg.contains("SECRET"))
        assertTrue(msg.contains("<url>"))
    }
}
