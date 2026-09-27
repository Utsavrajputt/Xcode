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
        onProgress: (GitCloneProgress) -> Unit,
    ): GitResult<File> = withContext(Dispatchers.IO) {
        try {
            val tracker = GitCloneProgressTracker(onProgress)
            val cmd = Git.cloneRepository()
                .setURI(url)
                .setDirectory(directory)
                .setCredentialsProvider(GitTokenCredentialsProvider(username, token))
                .setProgressMonitor(tracker.monitor)
            if (!branch.isNullOrBlank()) cmd.setBranch(branch.trim())
            val git = GitByteTracker.track(tracker::onBytesRead) { cmd.call() }
            git.close()
            tracker.finish()
            GitResult.Ok(directory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            GitResult.Err(GitErrorFactory.from(e, "Clone failed") { it.message ?: "Unknown error" })
        }
    }
}
