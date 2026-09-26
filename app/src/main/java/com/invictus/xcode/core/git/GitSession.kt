package com.invictus.xcode.core.git

import com.invictus.xcode.core.git.model.GitBranchInfo
import com.invictus.xcode.core.git.model.GitDiffLineType
import com.invictus.xcode.core.git.model.GitDiffRow
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitCommitSummary
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitTrackingInfo
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.core.git.model.GitWorkingTreeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.util.FS
import java.io.File

/** Thrown by [GitSession.commit] when no author identity is configured anywhere. */
class GitIdentityMissingException : Exception("Git author name/email not configured")

/**
 * One repo = one session. Every JGit call funnels through [ioOp] on [Dispatchers.IO]
 * and every mutation/refresh is serialized through [mutex], so snapshot+status never
 * interleave with a write op (plan section 3.4).
 */
class GitSession(
    private val workTree: File,
    private val globalIdentityFile: File? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    val repository: Repository by lazy {
        FileRepositoryBuilder().setWorkTree(workTree).setMustExist(true).build()
    }
    private val git: Git get() = Git(repository)

    val isRepo: Boolean get() = runCatching { File(workTree, ".git").isDirectory }.getOrDefault(false)

    private val globalConfig: FileBasedConfig?
        get() = globalIdentityFile?.let { FileBasedConfig(it, FS.DETECTED) }

    data class Identity(val name: String, val email: String)

    // ---- status / snapshot -------------------------------------------------

    /** Refresh snapshot + status together under the mutex; what the UI shows after any op. */
    suspend fun refresh(): GitResult<Pair<GitRepoSnapshot, GitWorkingTreeStatus>> =
        ioOp(TITLE_STATUS) { mutex.withLock { doSnapshot() to doStatus() } }

    suspend fun status(): GitResult<GitWorkingTreeStatus> =
        ioOp(TITLE_STATUS) { mutex.withLock { doStatus() } }

    // ---- stage / unstage ---------------------------------------------------

    suspend fun stage(path: String): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { git.add().addFilepattern(path.normalized()).call() }
        Unit
    }

    suspend fun unstage(path: String): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { git.reset().addPath(path.normalized()).call() }
        Unit
    }

    /** Stage everything: new + modified files, then deletions (git add -u). */
    suspend fun stageAll(): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock {
            git.add().addFilepattern(".").call()
            git.add().addFilepattern(".").setUpdate(true).call()
        }
        Unit
    }

    suspend fun unstageAll(): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { git.reset().setMode(ResetCommand.ResetType.MIXED).call() }
        Unit
    }

    // ---- commit ------------------------------------------------------------

    suspend fun commit(message: String, amend: Boolean): GitResult<GitCommitSummary> =
        ioOp(TITLE_COMMIT) {
            mutex.withLock {
                val identity = resolveIdentity() ?: throw GitIdentityMissingException()
                val commit = git.commit()
                    .setMessage(message)
                    .setAmend(amend)
                    .setAuthor(identity.name, identity.email)
                    .call()
                GitCommitSummary(
                    id = commit.name,
                    shortId = commit.abbreviate(7).name(),
                    message = commit.fullMessage,
                    author = commit.authorIdent.name,
                    timeMs = commit.authorIdent.`when`.time,
                )
            }
        }

    // ---- network (M6: token auth, non-force only) --------------------------

    suspend fun push(
        remote: String,
        credentials: CredentialsProvider?,
        onProgress: (GitProgress) -> Unit = {},
    ): GitResult<Unit> = ioOp(TITLE_PUSH) {
        mutex.withLock {
            git.push()
                .setRemote(remote)
                .setCredentialsProvider(credentials)
                .setProgressMonitor(gitProgressMonitor(onProgress))
                .call()
        }
        Unit
    }

    suspend fun pull(
        remote: String,
        branch: String?,
        credentials: CredentialsProvider?,
        onProgress: (GitProgress) -> Unit = {},
    ): GitResult<Unit> = ioOp(TITLE_PULL) {
        mutex.withLock {
            val cmd = git.pull()
                .setRemote(remote)
                .setCredentialsProvider(credentials)
                .setProgressMonitor(gitProgressMonitor(onProgress))
            if (!branch.isNullOrBlank()) cmd.setRemoteBranchName(branch.trim())
            cmd.call()
        }
        Unit
    }

    suspend fun fetch(
        remote: String,
        credentials: CredentialsProvider?,
        onProgress: (GitProgress) -> Unit = {},
    ): GitResult<Unit> = ioOp(TITLE_FETCH) {
        mutex.withLock {
            git.fetch()
                .setRemote(remote)
                .setCredentialsProvider(credentials)
                .setProgressMonitor(gitProgressMonitor(onProgress))
                .call()
        }
        Unit
    }

    // ---- diff (M7) -----------------------------------------------------------

    /** HEAD-vs-worktree diff for one repo-relative path, parsed into render rows. */
    suspend fun fileDiff(path: String): GitResult<GitFileDiffResult> =
        ioOp(TITLE_DIFF) { mutex.withLock { doFileDiff(path.normalized()) } }

    private fun doFileDiff(relPath: String): GitFileDiffResult {
        val headId = repository.resolve("HEAD")
        val reader = repository.newObjectReader()
        val oldTree: AbstractTreeIterator = try {
            if (headId != null) {
                val revWalk = RevWalk(repository)
                try {
                    val tree = revWalk.parseCommit(headId).tree
                    CanonicalTreeParser().apply { reset(reader, tree) }
                } finally {
                    revWalk.close()
                }
            } else {
                EmptyTreeIterator()
            }
        } finally {
            reader.close()
        }
        val out = java.io.ByteArrayOutputStream()
        git.diff()
            .setOldTree(oldTree)
            .setPathFilter(PathFilter.create(relPath))
            .setOutputStream(out)
            .call()
        val raw = out.toString("UTF-8")
        val base = DiffParser.parse(raw, relPath, File(workTree, relPath).absolutePath)
        // Image "before": pull the HEAD blob bytes so the UI can show old vs new.
        if (base.isImage && headId != null && base.oldImageBytes == null) {
            runCatching {
                val revWalk = RevWalk(repository)
                try {
                    val tree = revWalk.parseCommit(headId).tree
                    TreeWalk(repository).use { tw ->
                        tw.addTree(tree)
                        tw.isRecursive = true
                        tw.filter = PathFilter.create(relPath)
                        if (tw.next() && tw.getFileMode(0).objectType == org.eclipse.jgit.lib.Constants.OBJ_BLOB) {
                            return base.copy(oldImageBytes = repository.open(tw.getObjectId(0)).bytes)
                        }
                    }
                } finally {
                    revWalk.close()
                }
            }
        }
        return base
    }

    // ---- remotes / branches -------------------------------------------------

    suspend fun listRemotes(): GitResult<List<GitRemoteInfo>> = ioOp(TITLE_STATUS) {
        mutex.withLock {
            repository.config.getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION).map { name ->
                GitRemoteInfo(
                    name = name,
                    url = repository.config.getString(
                        ConfigConstants.CONFIG_REMOTE_SECTION,
                        name,
                        ConfigConstants.CONFIG_KEY_URL,
                    ) ?: "",
                )
            }
        }
    }

    suspend fun listBranches(): GitResult<List<GitBranchInfo>> = ioOp(TITLE_STATUS) {
        mutex.withLock {
            val current = runCatching { repository.branch }.getOrNull()
            git.branchList().call().map { ref ->
                GitBranchInfo(
                    name = ref.name.removePrefix("refs/heads/"),
                    isCurrent = ref.name.removePrefix("refs/heads/") == current,
                )
            }
        }
    }

    fun remoteUrl(remote: String): String? =
        runCatching {
            repository.config.getString(
                ConfigConstants.CONFIG_REMOTE_SECTION,
                remote,
                ConfigConstants.CONFIG_KEY_URL,
            )
        }.getOrNull()

    // ---- identity -----------------------------------------------------------

    suspend fun getIdentity(local: Boolean): GitResult<Identity?> = ioOp(TITLE_IDENTITY) {
        if (local) {
            val name = repository.config.getString(
                ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME,
            )
            val email = repository.config.getString(
                ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL,
            )
            if (name.isNullOrBlank() || email.isNullOrBlank()) null else Identity(name, email)
        } else {
            val cfg = globalConfig ?: return@ioOp null
            cfg.load()
            val name = cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME)
            val email = cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL)
            if (name.isNullOrBlank() || email.isNullOrBlank()) null else Identity(name, email)
        }
    }

    suspend fun setIdentity(name: String, email: String, local: Boolean): GitResult<Unit> =
        ioOp(TITLE_IDENTITY) {
            if (local) {
                repository.config.setString(
                    ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name,
                )
                repository.config.setString(
                    ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email,
                )
                repository.config.save()
            } else {
                val cfg = globalConfig ?: throw IllegalStateException("Global identity file unavailable")
                cfg.load()
                cfg.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name)
                cfg.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email)
                cfg.save()
            }
            Unit
        }

    // ---- internals ----------------------------------------------------------

    private fun resolveIdentity(): Identity? {
        val localName = repository.config.getString(
            ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME,
        )
        val localEmail = repository.config.getString(
            ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL,
        )
        if (!localName.isNullOrBlank() && !localEmail.isNullOrBlank()) {
            return Identity(localName, localEmail)
        }
        val cfg = globalConfig ?: return null
        runCatching { cfg.load() }
        val name = cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME)
        val email = cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL)
        return if (!name.isNullOrBlank() && !email.isNullOrBlank()) Identity(name, email) else null
    }

    private fun doSnapshot(): GitRepoSnapshot {
        val headId = runCatching { repository.resolve("HEAD")?.name }.getOrNull()
        val branch = runCatching { repository.branch }.getOrNull()
        val tracking = branch?.let { runCatching { trackingInfo(it) }.getOrNull() }
        return GitRepoSnapshot(
            gitRoot = workTree,
            hasCommits = headId != null,
            headId = headId,
            headName = branch,
            trackingInfo = tracking,
        )
    }

    private fun trackingInfo(branch: String): GitTrackingInfo? {
        val remote = repository.config.getString(
            ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE,
        ) ?: return null
        val merge = repository.config.getString(
            ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE,
        ) ?: return null
        val shortBranch = merge.removePrefix("refs/heads/")
        val localRef = repository.resolve("HEAD") ?: return null
        val remoteRef = repository.resolve("refs/remotes/$remote/$shortBranch") ?: return null
        val walk = RevWalk(repository)
        return try {
            walk.setRetainBody(false)
            GitTrackingInfo(
                remote = remote,
                branch = shortBranch,
                ahead = countRange(walk, localRef, remoteRef),
                behind = countRange(walk, remoteRef, localRef),
            )
        } finally {
            walk.close()
        }
    }

    /** Commits reachable from [from] but not from [to]. */
    private fun countRange(walk: RevWalk, from: ObjectId, to: ObjectId): Int {
        walk.reset()
        walk.markStart(walk.parseCommit(from))
        walk.markUninteresting(walk.parseCommit(to))
        var count = 0
        while (walk.next() != null) count++
        return count
    }

    private fun doStatus(): GitWorkingTreeStatus {
        val s = git.status().call()
        val staged = HashMap<String, GitStageState>()
        val unstaged = HashMap<String, GitWorkingState>()
        s.added.forEach { staged[it] = GitStageState.ADDED }
        s.changed.forEach { staged[it] = GitStageState.MODIFIED }
        s.removed.forEach { staged[it] = GitStageState.DELETED }
        s.modified.forEach { unstaged[it] = GitWorkingState.MODIFIED }
        s.missing.forEach { unstaged[it] = GitWorkingState.DELETED }
        s.untracked.forEach { unstaged[it] = GitWorkingState.UNTRACKED }
        s.conflicting.forEach {
            staged[it] = GitStageState.CONFLICT
            unstaged[it] = GitWorkingState.CONFLICT
        }
        val all = (staged.keys + unstaged.keys).sorted()
        return GitWorkingTreeStatus(
            changes = all.map {
                GitPathChange(
                    repoRelativePath = it,
                    staged = staged[it] ?: GitStageState.NONE,
                    unstaged = unstaged[it] ?: GitWorkingState.NONE,
                )
            },
            hasUnmerged = s.conflicting.isNotEmpty(),
        )
    }

    private suspend fun <T> ioOp(title: String, block: suspend () -> T): GitResult<T> =
        withContext(io) {
            try {
                GitResult.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepositoryNotFoundException) {
                GitResult.Err(GitErrorFactory.from(e, title) { "This folder is not a Git repository." })
            } catch (e: Throwable) {
                GitResult.Err(GitErrorFactory.from(e, title) { it.message ?: "Unknown error" })
            }
        }

    /** Windows-style separators must never reach addFilepattern/addPath (plan section 3.4). */
    private fun String.normalized(): String = replace(File.separatorChar, '/')

    companion object {
        private const val TITLE_STATUS = "Git status"
        private const val TITLE_STAGE = "Git stage"
        private const val TITLE_COMMIT = "Git commit"
        private const val TITLE_PUSH = "Git push"
        private const val TITLE_PULL = "Git pull"
        private const val TITLE_FETCH = "Git fetch"
        private const val TITLE_IDENTITY = "Git identity"
        private const val TITLE_DIFF = "Git diff"
    }
}


/** Unified-diff text -> [GitFileDiffResult]. Pure string work, no JGit; unit-testable. */
private object DiffParser {
    private val HUNK_RE = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private const val MAX_ROWS = 4000

    fun parse(raw: String, relPath: String, workFilePath: String): GitFileDiffResult {
        val rows = ArrayList<GitDiffRow>()
        var isBinary = false
        var truncated = false
        var leftNo = 0
        var rightNo = 0
        val pendingRemoved = ArrayList<Pair<Int, String>>()

        fun flushRemoved() {
            while (pendingRemoved.isNotEmpty()) {
                val (ln, text) = pendingRemoved.removeAt(0)
                rows += GitDiffRow(ln, text, GitDiffLineType.REMOVED, null, null, GitDiffLineType.PADDING)
            }
        }

        for (line in raw.lineSequence()) {
            if (rows.size >= MAX_ROWS) { truncated = true; break }
            when {
                line.startsWith("Binary files") || line.startsWith("GIT binary patch") -> isBinary = true
                line.startsWith("@@") -> {
                    flushRemoved()
                    val m = HUNK_RE.find(line) ?: continue
                    leftNo = m.groupValues[1].toInt()
                    rightNo = m.groupValues[2].toInt()
                }
                line.startsWith("---") || line.startsWith("+++") ||
                    line.startsWith("diff ") || line.startsWith("index ") -> Unit
                line.startsWith("-") -> pendingRemoved += leftNo++ to line.substring(1)
                line.startsWith("+") -> {
                    val text = line.substring(1)
                    if (pendingRemoved.isNotEmpty()) {
                        val (ln, oldText) = pendingRemoved.removeAt(0)
                        val (l, r) = intraline(oldText, text)
                        rows += GitDiffRow(
                            ln, oldText, GitDiffLineType.REMOVED,
                            rightNo++, text, GitDiffLineType.ADDED, l, r,
                        )
                    } else {
                        rows += GitDiffRow(null, null, GitDiffLineType.PADDING, rightNo++, text, GitDiffLineType.ADDED)
                    }
                }
                line.startsWith(" ") -> {
                    flushRemoved()
                    val text = line.substring(1)
                    rows += GitDiffRow(leftNo++, text, GitDiffLineType.CONTEXT, rightNo++, text, GitDiffLineType.CONTEXT)
                }
            }
        }
        flushRemoved()

        val isImage = relPath.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
        return GitFileDiffResult(
            path = relPath,
            oldLabel = "HEAD:$relPath",
            newLabel = relPath,
            isBinary = isBinary,
            isImage = isImage,
            rows = rows,
            truncated = truncated,
            workFilePath = workFilePath,
        )
    }

    /** Common prefix/suffix trim -> differing character ranges of a paired line pair. */
    private fun intraline(old: String, new: String): Pair<List<IntRange>, List<IntRange>> {
        var prefix = 0
        val min = minOf(old.length, new.length)
        while (prefix < min && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < min - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
        ) suffix++
        val leftRange =
            if (old.length - suffix > prefix) listOf(prefix until old.length - suffix) else emptyList()
        val rightRange =
            if (new.length - suffix > prefix) listOf(prefix until new.length - suffix) else emptyList()
        return leftRange to rightRange
    }
}
