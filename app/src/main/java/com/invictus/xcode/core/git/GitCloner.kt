package com.invictus.xcode.core.git

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File

/** Clone has no repo yet, so it lives outside [GitSession]. */
object GitCloner {

    suspend fun clone(
        url: String,
        username: String,
        token: String,
        directory: File,
        branch: String?,
        onProgress: (GitProgress) -> Unit,
    ): GitResult<File> = withContext(Dispatchers.IO) {
        try {
            val cmd = Git.cloneRepository()
                .setURI(url)
                .setDirectory(directory)
                .setCredentialsProvider(GitTokenCredentialsProvider(username, token))
                .setProgressMonitor(gitProgressMonitor(onProgress))
            if (!branch.isNullOrBlank()) cmd.setBranch(branch.trim())
            val git = cmd.call()
            git.close()
            GitResult.Ok(directory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            GitResult.Err(GitErrorFactory.from(e, "Clone failed") { it.message ?: "Unknown error" })
        }
    }
}
