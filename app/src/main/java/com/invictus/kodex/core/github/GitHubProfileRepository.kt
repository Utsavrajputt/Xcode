package com.invictus.kodex.core.github

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Live GitHub profile for a stored token (`GET https://api.github.com/user`), cached per
 * host in `filesDir/github_profiles.json` and refreshed once it is older than 24h.
 * The token itself is never written here (it stays in [com.invictus.kodex.core.security.GitCredentialStore]).
 * Only github.com is supported; other hosts have no comparable API.
 */
class GitHubProfileRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun supports(host: String): Boolean = host.trim().lowercase() == GITHUB_HOST

    @Synchronized
    fun cached(host: String): GitHubProfile? =
        readAll()[host.lowercase()]?.let { runCatching { fromJson(it) }.getOrNull() }

    fun isStale(profile: GitHubProfile, now: Long = System.currentTimeMillis()): Boolean =
        now - profile.fetchedAt >= TTL_MS

    @Synchronized
    fun clear(host: String) {
        val all = readAll()
        if (all.remove(host.lowercase()) != null) writeAll(all)
    }

    /**
     * Fetches and caches the profile. On a network/server error returns the stale cached
     * profile (if any); on 401 the token is bad, so the cache is dropped and null returned.
     */
    suspend fun fetch(host: String, token: String): GitHubProfile? = withContext(Dispatchers.IO) {
        if (!supports(host)) return@withContext null
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            client.newCall(req).execute().use { res ->
                when {
                    res.code == 401 -> { clear(host); null }
                    !res.isSuccessful -> cached(host)
                    else -> {
                        val profile = fromApi(JSONObject(res.body?.string().orEmpty()))
                        store(host, profile)
                        profile
                    }
                }
            }
        }.getOrElse { cached(host) }
    }

    @Synchronized
    private fun store(host: String, profile: GitHubProfile) {
        val all = readAll()
        all[host.lowercase()] = toJson(profile)
        writeAll(all)
    }

    private fun readAll(): MutableMap<String, JSONObject> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf<String, JSONObject>()
        val json = JSONObject(file.readText())
        val out = LinkedHashMap<String, JSONObject>()
        for (k in json.keys()) out[k] = json.getJSONObject(k)
        out
    }.getOrDefault(mutableMapOf())

    private fun writeAll(all: Map<String, JSONObject>) {
        val json = JSONObject()
        all.forEach { (k, v) -> json.put(k, v) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun optString(j: JSONObject, key: String): String? =
        if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotBlank() }

    private fun fromApi(j: JSONObject) = GitHubProfile(
        login = j.getString("login"),
        name = optString(j, "name"),
        email = optString(j, "email"),
        avatarUrl = j.getString("avatar_url"),
        publicRepos = j.optInt("public_repos"),
        followers = j.optInt("followers"),
        following = j.optInt("following"),
        fetchedAt = System.currentTimeMillis(),
    )

    private fun toJson(p: GitHubProfile) = JSONObject()
        .put("login", p.login)
        .put("name", p.name ?: JSONObject.NULL)
        .put("email", p.email ?: JSONObject.NULL)
        .put("avatar_url", p.avatarUrl)
        .put("public_repos", p.publicRepos)
        .put("followers", p.followers)
        .put("following", p.following)
        .put("fetched_at", p.fetchedAt)

    private fun fromJson(j: JSONObject) = GitHubProfile(
        login = j.getString("login"),
        name = optString(j, "name"),
        email = optString(j, "email"),
        avatarUrl = j.getString("avatar_url"),
        publicRepos = j.optInt("public_repos"),
        followers = j.optInt("followers"),
        following = j.optInt("following"),
        fetchedAt = j.optLong("fetched_at"),
    )

    companion object {
        private const val FILE_NAME = "github_profiles.json"
        private const val GITHUB_HOST = "github.com"
        private val TTL_MS = TimeUnit.HOURS.toMillis(24)
    }
}
