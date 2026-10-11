package com.invictus.kodex.github.data

import com.invictus.kodex.core.git.GitResult
import com.invictus.kodex.core.git.GitSessionRegistry
import com.invictus.kodex.github.api.GhRepoRef
import com.invictus.kodex.github.auth.GitHubTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The four entry states of plan 6.1, in resolution order. */
sealed interface GitHubResolution {
    data object NotGit : GitHubResolution
    data object NoGitHubRemote : GitHubResolution
    data object NoToken : GitHubResolution
    data class Ready(val repo: GhRepoRef) : GitHubResolution
}

/** Binds a project to its GitHub repo through the existing GitSession (read-only, shared mutex). */
class GitHubProjectResolver(private val tokens: GitHubTokenProvider) {

    suspend fun resolve(projectPath: String): GitHubResolution {
        val root = File(projectPath)
        val isRepo = withContext(Dispatchers.IO) { File(root, ".git").exists() }
        if (!isRepo) return GitHubResolution.NotGit

        // A corrupt .git must read as "not a repository", never crash the screen.
        val remotes = runCatching {
            val session = GitSessionRegistry.acquire(root)
            try {
                when (val r = session.listRemotes()) {
                    is GitResult.Ok -> r.value.map { it.name to it.url }
                    is GitResult.Err -> emptyList()
                }
            } finally {
                GitSessionRegistry.release(root)
            }
        }.getOrElse { return GitHubResolution.NotGit }
        val repo = RepoDetector.detect(remotes) ?: return GitHubResolution.NoGitHubRemote
        if (!tokens.hasToken()) return GitHubResolution.NoToken
        return GitHubResolution.Ready(repo)
    }
}
