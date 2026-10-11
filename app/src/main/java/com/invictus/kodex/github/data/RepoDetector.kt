package com.invictus.kodex.github.data

import com.invictus.kodex.core.github.GitHubRepoApi
import com.invictus.kodex.github.api.GhRepoRef

/**
 * Remote URL -> `owner/repo` (plan 6.1). Prefers `origin`, otherwise the first remote whose host is
 * github.com; handles HTTPS and SSH forms and the `.git` suffix via the existing
 * [GitHubRepoApi.parseRepo]. Nothing is stored: detection re-runs every time the screen opens.
 */
object RepoDetector {
    /** [remotes] = (remote name, url) in config order. */
    fun detect(remotes: List<Pair<String, String>>): GhRepoRef? {
        val ordered = remotes.sortedByDescending { it.first == "origin" } // stable: origin first, rest keep order
        for ((_, url) in ordered) {
            GitHubRepoApi.parseRepo(url)?.let { (owner, name) -> return GhRepoRef(owner, name) }
        }
        return null
    }
}
