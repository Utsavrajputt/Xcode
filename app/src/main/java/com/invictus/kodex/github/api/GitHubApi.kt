package com.invictus.kodex.github.api

import org.json.JSONObject
import java.io.File

/** One suspend function per endpoint (plan section 7.1/7.2). Returns models or throws [GitHubError]. */
class GitHubApi(private val http: GitHubHttp, private val repo: GhRepoRef) {

    val rateLimit: RateLimit? get() = http.rateLimit

    private val base get() = "/repos/${repo.owner}/${repo.name}"

    /** `GET /repos/{o}/{r}`: default branch etc. */
    suspend fun getRepo(): GhRepoRef {
        val j = JSONObject(http.getJson(base))
        return repo.copy(defaultBranch = j.str("default_branch"))
    }

    /** `GET /repos/{o}/{r}/actions/workflows` (first 100). */
    suspend fun listWorkflows(): List<GhWorkflow> {
        val arr = JSONObject(http.getJson("$base/actions/workflows", mapOf("per_page" to "100"))).optJSONArray("workflows")
        return buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                add(GhWorkflow(w.getLong("id"), w.optString("name"), w.optString("path"), w.optString("state")))
            }
        }
    }

    /**
     * `GET /repos/{o}/{r}/actions/runs` or the workflow-scoped variant. [status] accepts both
     * status and conclusion values (`in_progress`, `failure`, `cancelled`, ...).
     */
    suspend fun listRuns(page: Int, perPage: Int = 30, status: String? = null, workflowId: Long? = null): Page<GhRun> {
        val path = if (workflowId != null) "$base/actions/workflows/$workflowId/runs" else "$base/actions/runs"
        val q = buildMap {
            put("per_page", perPage.toString()); put("page", page.toString())
            if (status != null) put("status", status)
        }
        val j = JSONObject(http.getJson(path, q))
        val arr = j.optJSONArray("workflow_runs")
        val items = buildList { if (arr != null) for (i in 0 until arr.length()) add(GhRun.fromJson(arr.getJSONObject(i))) }
        val total = j.optInt("total_count")
        return Page(items, page, hasMore = page * perPage < total && items.isNotEmpty(), total = total)
    }

    suspend fun getRun(runId: Long): GhRun = GhRun.fromJson(JSONObject(http.getJson("$base/actions/runs/$runId")))

    /** `GET .../runs/{id}/jobs?filter=latest` */
    suspend fun listJobs(runId: Long): List<GhJob> {
        val arr = JSONObject(http.getJson("$base/actions/runs/$runId/jobs", mapOf("filter" to "latest", "per_page" to "100")))
            .optJSONArray("jobs")
        return buildList { if (arr != null) for (i in 0 until arr.length()) add(GhJob.fromJson(arr.getJSONObject(i))) }
    }

    /** `GET .../jobs/{id}/logs` (302 -> plain text) streamed to [dest]. True when truncated at [maxBytes]. */
    suspend fun downloadJobLog(jobId: Long, dest: File, maxBytes: Long): Boolean =
        http.downloadText("$base/actions/jobs/$jobId/logs", dest, maxBytes)

    suspend fun cancel(runId: Long) { http.send("POST", "$base/actions/runs/$runId/cancel") }

    suspend fun forceCancel(runId: Long) { http.send("POST", "$base/actions/runs/$runId/force-cancel") }

    suspend fun rerun(runId: Long) { http.send("POST", "$base/actions/runs/$runId/rerun") }

    suspend fun rerunFailedJobs(runId: Long) { http.send("POST", "$base/actions/runs/$runId/rerun-failed-jobs") }

    suspend fun deleteRun(runId: Long) { http.send("DELETE", "$base/actions/runs/$runId") }
}
