package com.invictus.kodex.github

import com.invictus.kodex.github.api.GhArtifact
import com.invictus.kodex.github.api.GhDeployment
import com.invictus.kodex.github.api.GhRun
import com.invictus.kodex.github.api.GitHubError
import com.invictus.kodex.github.data.CleanupFilters
import com.invictus.kodex.github.data.CleanupItem
import com.invictus.kodex.github.data.CleanupKind
import com.invictus.kodex.github.data.CleanupPlanner
import com.invictus.kodex.github.data.CleanupRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CleanupPlannerTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun ago(days: Long) = now.minusSeconds(days * 86_400)

    private fun run(id: Long, ageDays: Long, conclusion: String?, status: String = "completed", wf: Long = 1) = GhRun(
        id = id, workflowId = wf, name = "Android build", runNumber = id.toInt(), status = status, conclusion = conclusion,
        branch = "main", sha = "abcdef0123", commitMessage = "msg", event = "push",
        createdAt = ago(ageDays), startedAt = ago(ageDays), updatedAt = ago(ageDays),
    )

    private fun plan(runs: List<GhRun>, f: CleanupFilters) = CleanupPlanner.planRuns(runs, f, now, { "Build" })

    private val runs = listOf(
        run(1, 40, "failure"), run(2, 35, "cancelled"), run(3, 20, "success"),
        run(4, 10, "failure"), run(5, 2, "success"), run(6, 1, null, status = "in_progress"),
    )

    @Test fun onlyCompletedRunsAreSelectable() {
        val p = plan(runs, CleanupFilters())
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), p.items.map { it.id })
        assertFalse(6L in p.items.map { it.id })
    }

    @Test fun failedAndCancelledFlagsAreOred() {
        assertEquals(listOf(4L, 1L), plan(runs, CleanupFilters(failed = true)).items.map { it.id })
        assertEquals(listOf(2L), plan(runs, CleanupFilters(cancelled = true)).items.map { it.id })
        assertEquals(listOf(4L, 2L, 1L), plan(runs, CleanupFilters(failed = true, cancelled = true)).items.map { it.id })
    }

    @Test fun olderThanUsesInjectedClock() {
        assertEquals(listOf(2L, 1L), plan(runs, CleanupFilters(olderThanDays = 30)).items.map { it.id })
        assertEquals(listOf(4L, 3L, 2L, 1L), plan(runs, CleanupFilters(olderThanDays = 7)).items.map { it.id })
    }

    @Test fun keepLatestKeepsNewestNCompletedRuns() {
        // Newest two completed runs (5 and 4) are never offered, whatever the other filters say.
        assertEquals(listOf(3L, 2L, 1L), plan(runs, CleanupFilters(keepLatest = 2)).items.map { it.id })
        assertEquals(listOf(1L), plan(runs, CleanupFilters(keepLatest = 2, failed = true)).items.map { it.id })
    }

    @Test fun workflowFilterScopesEverything() {
        val mixed = runs + run(7, 50, "failure", wf = 2)
        assertEquals(listOf(7L), plan(mixed, CleanupFilters(workflowId = 2)).items.map { it.id })
    }

    @Test fun newestSuccessfulRunIsFlaggedAndNotPreselected() {
        val p = plan(runs, CleanupFilters())
        assertTrue(p.items.first { it.id == 5L }.isLatestBuild)
        assertFalse(p.items.first { it.id == 3L }.isLatestBuild)
        assertFalse(5L in p.defaultSelection)
        assertTrue(3L in p.defaultSelection)
    }

    @Test fun artifactSizesAttachToRuns() {
        val p = CleanupPlanner.planRuns(runs, CleanupFilters(), now, { "Build" }, mapOf(5L to 1_000L))
        assertEquals(1_000L, p.items.first { it.id == 5L }.sizeBytes)
    }

    private fun art(id: Long, ageDays: Long, run: Long?, expired: Boolean = false, expiresInDays: Long = 14 - ageDays) = GhArtifact(
        id = id, name = "kodex-arm64-v8a-$id", sizeBytes = 100, expired = expired, createdAt = ago(ageDays),
        expiresAt = now.plusSeconds(expiresInDays * 86_400), runId = run, branch = "main", sha = "abc",
    )

    @Test fun artifactsExpiredOnlyAndLatestBuild() {
        val list = listOf(art(1, 20, 10, expired = true), art(2, 16, 20, expiresInDays = -2), art(3, 3, 30), art(4, 3, 30), art(5, 8, 40))
        val expired = CleanupPlanner.planArtifacts(list, CleanupFilters(kind = CleanupKind.Artifacts, expiredOnly = true), now)
        assertEquals(listOf(2L, 1L), expired.items.map { it.id })

        val all = CleanupPlanner.planArtifacts(list, CleanupFilters(kind = CleanupKind.Artifacts), now)
        // Both artifacts of the newest live run (30) are the "latest build".
        assertEquals(setOf(3L, 4L), all.items.filter { it.isLatestBuild }.map { it.id }.toSet())
        assertFalse(3L in all.defaultSelection)
    }

    @Test fun deploymentsOlderThanAndKeepLatest() {
        fun dep(id: Long, age: Long) = GhDeployment(id, "github-pages", "main", "abcdef0", ago(age), "")
        val list = listOf(dep(1, 90), dep(2, 40), dep(3, 5))
        val p = CleanupPlanner.planDeployments(list, CleanupFilters(kind = CleanupKind.Deployments, olderThanDays = 30, keepLatest = 1), now)
        assertEquals(listOf(2L, 1L), p.items.map { it.id })
        assertTrue(p.items.none { it.isLatestBuild })
    }

    // ---- runner ---------------------------------------------------------------------------

    private fun item(id: Long) = CleanupItem(id, CleanupKind.Runs, "run $id", "", 0, null, false)

    private fun runner(sleeps: MutableList<Long>, nowSec: Long = 1_000) =
        CleanupRunner(spacingMs = 400, sleep = { sleeps += it }, nowEpochSec = { nowSec }, maxRateWaitSec = 90)

    @Test fun runnerDeletesSequentiallyWithSpacing() {
        val sleeps = mutableListOf<Long>()
        val order = mutableListOf<Long>()
        val res = runBlocking {
            runner(sleeps).run((1L..3L).map { item(it) }, { false }, { order += it.id }, { _, _ -> })
        }
        assertEquals(listOf(1L, 2L, 3L), order)
        assertEquals(3, res.deleted)
        assertEquals(listOf(400L, 400L), sleeps) // between calls, not after the last
    }

    @Test fun runnerTreatsNotFoundAsDeletedAndRecordsOtherFailures() {
        val res = runBlocking {
            runner(mutableListOf()).run(
                (1L..3L).map { item(it) }, { false },
                { when (it.id) { 1L -> throw GitHubError.NotFound(); 2L -> throw GitHubError.MissingPermission("no"); else -> Unit } },
                { _, _ -> },
            )
        }
        assertEquals(2, res.deleted)
        assertEquals(listOf(2L), res.failed.map { it.item.id })
        assertTrue(res.notAttempted.isEmpty())
    }

    @Test fun runnerStopsWhenAsked() {
        var stop = false
        val res = runBlocking {
            runner(mutableListOf()).run((1L..5L).map { item(it) }, { stop }, { if (it.id == 2L) stop = true }, { _, _ -> })
        }
        assertEquals(2, res.deleted)
        assertEquals(listOf(3L, 4L, 5L), res.notAttempted.map { it.id })
    }

    @Test fun runnerWaitsOutShortRateLimitThenRetriesOnce() {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val res = runBlocking {
            runner(sleeps, nowSec = 1_000).run(
                listOf(item(1)), { false },
                { if (calls++ == 0) throw GitHubError.RateLimited(1_030L) },
                { _, _ -> },
            )
        }
        assertEquals(1, res.deleted)
        assertEquals(2, calls)
        assertEquals(31_000L, sleeps.first())
    }

    @Test fun runnerStopsOnLongRateLimit() {
        val res = runBlocking {
            runner(mutableListOf(), nowSec = 1_000).run(
                (1L..3L).map { item(it) }, { false },
                { if (it.id == 2L) throw GitHubError.RateLimited(5_000L) },
                { _, _ -> },
            )
        }
        assertEquals(1, res.deleted)
        assertEquals(listOf(2L), res.failed.map { it.item.id })
        assertEquals(listOf(3L), res.notAttempted.map { it.id })
    }
}
