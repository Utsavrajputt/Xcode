package com.invictus.kodex.github.data

import com.invictus.kodex.github.api.GhJob
import com.invictus.kodex.github.api.GhRepoRef
import com.invictus.kodex.github.api.GhRun
import com.invictus.kodex.github.api.GhWorkflow
import com.invictus.kodex.github.api.GitHubApi
import com.invictus.kodex.github.api.Page
import com.invictus.kodex.github.api.RateLimit
import java.io.File

/** What the Runs section filters by (plan 6.1 card 4). */
enum class RunStatusFilter(val apiStatus: String?) {
    All(null), Failed("failure"), Running("in_progress"), Cancelled("cancelled")
}

/** Result of loading a run's logs (plan 6.3 / 8.1). */
data class RunLog(
    val jobs: List<GhJob>,
    val job: GhJob?,
    /** Whole log on disk; null when the run has no jobs. Owned by the caller (delete when done). */
    val file: File?,
    /** The log was larger than the download cap and its start was dropped. */
    val truncatedOnDisk: Boolean,
    val excerpt: String,
)

/** Domain operations on top of [GitHubApi] (plan section 3). */
class GitHubRepository(
    private val api: GitHubApi,
    private val repo: GhRepoRef,
    private val logDir: File,
) {
    val rateLimit: RateLimit? get() = api.rateLimit

    suspend fun repoInfo(): GhRepoRef = api.getRepo()
    suspend fun workflows(): List<GhWorkflow> = api.listWorkflows()

    suspend fun runs(page: Int, filter: RunStatusFilter, workflowId: Long?): Page<GhRun> =
        api.listRuns(page = page, status = filter.apiStatus, workflowId = workflowId)

    suspend fun run(runId: Long): GhRun = api.getRun(runId)
    suspend fun jobs(runId: Long): List<GhJob> = api.listJobs(runId)

    suspend fun cancel(runId: Long) = api.cancel(runId)
    suspend fun forceCancel(runId: Long) = api.forceCancel(runId)
    suspend fun rerunAll(runId: Long) = api.rerun(runId)
    suspend fun rerunFailed(runId: Long) = api.rerunFailedJobs(runId)
    suspend fun delete(runId: Long) = api.deleteRun(runId)

    /**
     * Fetches the log of [jobId] (default: first failed job, else the last job) to a temp file and
     * builds the error excerpt. Throws a GitHubError (404/410 = logs expired).
     */
    suspend fun loadRunLog(run: GhRun, workflowName: String, jobId: Long? = null): RunLog {
        val jobs = api.listJobs(run.id)
        val header = LogExtractor.Header(repo.fullName, workflowName, run.runNumber, run.branch, run.shortSha, null)
        if (jobs.isEmpty()) {
            return RunLog(jobs, null, null, false, LogExtractor.noJobsText(header, run.name, run.conclusion))
        }
        val job = jobs.firstOrNull { it.id == jobId } ?: jobs.firstOrNull { it.isFailed } ?: jobs.last()
        logDir.mkdirs()
        LogFiles.cleanupDir(logDir)
        val file = File(logDir, "job-${job.id}-${System.nanoTime()}.log")
        val truncated = api.downloadJobLog(job.id, file, LogFiles.MAX_DOWNLOAD_BYTES)
        val tail = LogFiles.readTail(file)
        val excerpt = LogExtractor.excerpt(tail.lines, header.copy(failedStep = job.failedStepName))
        return RunLog(jobs, job, file, truncated, excerpt)
    }
}
