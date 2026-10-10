package com.invictus.kodex.core.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.jgit.transport.URIish
import org.json.JSONObject
import java.io.IOException

/** The few GitHub REST calls that git itself can't do. */
object GitHubRepoApi {

    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    /** "owner" to "repo" from https://github.com/owner/repo(.git) or git@github.com:owner/repo.git; null if not GitHub. */
    fun parseRepo(remoteUrl: String): Pair<String, String>? = runCatching {
        val uri = URIish(remoteUrl)
        if (!uri.host.equals("github.com", ignoreCase = true)) return null
        val parts = uri.path.trim('/').removeSuffix(".git").split('/')
        if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
    }.getOrNull()

    /**
     * PATCH /repos/{owner}/{repo} { "default_branch": branch }. Needs a token that may administer the
     * repo (classic: `repo` scope; fine-grained: Administration = write). Throws [IOException] with
     * GitHub's own message on failure.
     */
    @Throws(IOException::class)
    suspend fun setDefaultBranch(owner: String, repo: String, branch: String, token: String) {
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("default_branch", branch).toString().toRequestBody(json)
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .patch(body)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) return@use
                val detail = runCatching { JSONObject(resp.body?.string().orEmpty()).optString("message") }
                    .getOrNull().orEmpty()
                val hint = when (resp.code) {
                    401 -> "The token was rejected."
                    403, 404 -> "Repository not found, or the token isn't allowed to change repository settings."
                    422 -> "GitHub doesn't have a branch named '$branch' — push it first."
                    else -> ""
                }
                throw IOException("GitHub ${resp.code}: ${listOf(detail, hint).filter { it.isNotEmpty() }.joinToString(" ")}")
            }
        }
    }
}
