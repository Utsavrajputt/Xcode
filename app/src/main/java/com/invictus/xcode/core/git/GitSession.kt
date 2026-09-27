package com.invictus.xcode.core.git

import com.invictus.xcode.core.git.model.GitBranchDetail
import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.MergeOutcome
import com.invictus.xcode.core.git.model.GitBranchInfo
import com.invictus.xcode.core.git.model.GitDiffLineType
import com.invictus.xcode.core.git.model.GitDiffRow
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitCommitSummary
import com.invictus.xcode.core.git.model.GitLogSearchMode
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.git.model.GitResetMode
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitStashInfo
import com.invictus.xcode.core.git.model.GitTagInfo
import com.invictus.xcode.core.git.model.GitTrackingInfo
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.core.git.model.GitWorkingTreeStatus
import com.invictus.xcode.core.git.model.RebaseOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.errors.LockFailedException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
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
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** Thrown by [GitSession.commit] when no author identity is configured anywhere. */
class GitIdentityMissingException : Exception("Git author name/email not configured")

/**
 * One repo = one session. Every Git-feature screen (Source Control, Branches, History,
 * Tags, Stash, Remotes, Credentials, Onboarding) now shares the same [GitSession] instance
 * for a given [workTree] via [GitSessionRegistry] instead of each building its own - so the
 * underlying JGit [Repository], with its parsed pack indexes and object caches, only gets
 * opened once per repo instead of once per screen visit. (The file tree's status decorations
 * and the GitHub-identity settings screen still hold their own private instances outside
 * that sharing - the former polls independently of any one screen being open, the latter
 * usually isn't pointed at a real repo at all.)
 *
 * Every JGit call funnels through [ioOp] on [Dispatchers.IO], and every mutation/refresh is
 * serialized through [mutex] - which is keyed by the repo's canonical path in [mutexRegistry]
 * and shared across every instance pointed at that path (relevant for the two private-instance
 * exceptions above), so snapshot+status never interleave with a write op or with another
 * screen's op on the same repo (plan section 3.4).
 *
 * [ioOp] also recovers from a stale `index.lock` left behind by a killed process: since
 * [mutex] guarantees no other in-process op is running when the lock file is found, it's
 * safe to delete and retry once.
 */
class GitSession(
    private val workTree: File,
    private val globalIdentityFile: File? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val repoKey: String by lazy {
        runCatching { workTree.canonicalPath }.getOrDefault(workTree.absolutePath)
    }
    private val mutex: Mutex get() = mutexRegistry.getOrPut(repoKey) { Mutex() }

    private val repositoryLazy: Lazy<Repository> = lazy {
        FileRepositoryBuilder().setWorkTree(workTree).setMustExist(true).build()
    }
    val repository: Repository by repositoryLazy
    private val git: Git get() = Git(repository)

    val isRepo: Boolean get() = runCatching { File(workTree, ".git").isDirectory }.getOrDefault(false)

    /**
     * Releases the JGit [Repository] this session opened - closes its pack file handles and
     * drops its object/ref caches. No-op if [repository] was never touched (nothing to
     * release). Only [GitSessionRegistry] should call this: it's the only thing that tracks
     * whether another screen still shares this exact [GitSession] instance, and closing a
     * [Repository] out from under a screen still using it would break that screen.
     */
    fun close() {
        if (repositoryLazy.isInitialized()) repository.close()
    }

    /**
     * Status from the most recent [doStatus] call, kept only so [stageAll] can skip its
     * second (deletions-only) `AddCommand` walk when nothing was deleted last we checked.
     * Best-effort: never read under lock for correctness, only as a cheap "probably empty"
     * hint — a stale/missing value just means we don't skip the walk.
     */
    @Volatile private var lastStatus: GitWorkingTreeStatus? = null

    private val globalConfig: FileBasedConfig?
        get() = globalIdentityFile?.let { FileBasedConfig(it, FS.DETECTED) }

    data class Identity(val name: String, val email: String)

    // ---- M9: onboarding ------------------------------------------------------

    /** Create a fresh repository in [workTree] (`git init`). Safe on a folder without .git. */
    suspend fun initRepository(): GitResult<Unit> = ioOp(TITLE_INIT) {
        mutex.withLock {
            Git.init().setDirectory(workTree).call().close()
        }
        Unit
    }

    /** Make [branch] track [remote]/[branch] (`branch.<name>.remote` + `branch.<name>.merge`). */
    suspend fun setUpstream(remote: String, branch: String): GitResult<Unit> = ioOp(TITLE_REMOTE) {
        mutex.withLock {
            repository.config.setString(
                ConfigConstants.CONFIG_BRANCH_SECTION, branch, "remote", remote,
            )
            repository.config.setString(
                ConfigConstants.CONFIG_BRANCH_SECTION, branch, "merge", "refs/heads/$branch",
            )
            repository.config.save()
        }
        Unit
    }

    // ---- status / snapshot -------------------------------------------------

    /** Refresh snapshot + status together under the mutex; what the UI shows after any op. */
    suspend fun refresh(): GitResult<Pair<GitRepoSnapshot, GitWorkingTreeStatus>> =
        ioOp(TITLE_STATUS) { mutex.withLock { doSnapshot() to doStatus() } }

    /**
     * Snapshot + status + remotes in one [ioOp]/lock/`withContext(io)` hop instead of the
     * two separate suspend calls (`listRemotes()` then `refresh()`) the initial screen load
     * and every full refresh used to make sequentially. `listRemotes()` is a couple of
     * config reads and was never the slow part, but paying for a second dispatcher hop and
     * a second mutex acquire back-to-back before every screen open added latency for free.
     */
    suspend fun refreshFull(): GitResult<Triple<GitRepoSnapshot, GitWorkingTreeStatus, List<GitRemoteInfo>>> =
        ioOp(TITLE_STATUS) {
            mutex.withLock { Triple(doSnapshot(), doStatus(), readRemotes()) }
        }

    suspend fun status(): GitResult<GitWorkingTreeStatus> =
        ioOp(TITLE_STATUS) { mutex.withLock { doStatus() } }

    // ---- stage / unstage ---------------------------------------------------

    suspend fun stage(path: String): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { fastAddSingle(path.normalized()) }
        Unit
    }

    /**
     * Stages exactly one path without going through JGit's [org.eclipse.jgit.api.AddCommand].
     * `AddCommand` always does a recursive [TreeWalk] over the *entire* DirCache + working
     * tree (the path pattern only prunes which subtrees get walked, it doesn't avoid the
     * walk itself) — for one file that costs about as much as a full `status()`, which is
     * exactly the "staging one file feels as slow as opening Source Control" complaint.
     * A [org.eclipse.jgit.dircache.DirCacheEditor.PathEdit] instead touches only this one
     * DirCache entry directly: no treewalk, no stat'ing the rest of the working tree.
     */
    private fun fastAddSingle(path: String) {
        val file = File(workTree, path)
        if (file.isDirectory || java.nio.file.Files.isSymbolicLink(file.toPath())) {
            // Submodule (gitlink) or symlink: a gitlink needs the submodule's own HEAD
            // commit id, not a blob of file bytes, and a symlink's blob content is the
            // link *target string*, not — as a plain FileInputStream read would give us
            // — the bytes of whatever the link resolves to. Both need JGit's own
            // tree-aware handling. Rare in practice, so falling back to the slower but
            // correct AddCommand here doesn't cost much.
            git.add().addFilepattern(path).call()
            return
        }
        val dc = repository.lockDirCache()
        var success = false
        try {
            val editor = dc.editor()
            if (!file.exists()) {
                // "git add" on a path that's gone stages the deletion.
                editor.add(DirCacheEditor.DeletePath(path))
            } else {
                editor.add(object : DirCacheEditor.PathEdit(path) {
                    override fun apply(ent: DirCacheEntry) {
                        ent.fileMode =
                            if (file.canExecute()) FileMode.EXECUTABLE_FILE else FileMode.REGULAR_FILE
                        ent.lastModified = file.lastModified()
                        val length = file.length()
                        ent.length = length.toInt()
                        repository.newObjectInserter().use { inserter ->
                            ent.setObjectId(
                                FileInputStream(file).use { input ->
                                    inserter.insert(Constants.OBJ_BLOB, length, input)
                                }
                            )
                            inserter.flush()
                        }
                    }
                })
            }
            editor.commit()
            success = true
        } finally {
            if (!success) dc.unlock()
        }
    }

    suspend fun unstage(path: String): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { fastResetSingle(path.normalized()) }
        Unit
    }

    /**
     * Unstages exactly one path without JGit's `ResetCommand`, which — same story as
     * `AddCommand` — walks the whole index + HEAD tree even for one path.
     * [TreeWalk.forPath] does a targeted descent through just this path's tree entries
     * (no full recursive walk) to find what HEAD has there, then a
     * [DirCacheEditor.PathEdit] rewrites only this one DirCache entry to match it.
     */
    private fun fastResetSingle(path: String) {
        val headCommitId = repository.resolve(Constants.HEAD)
        val headTreeId = headCommitId?.let { id ->
            RevWalk(repository).use { it.parseCommit(id).tree }
        }
        val dc = repository.lockDirCache()
        var success = false
        try {
            val editor = dc.editor()
            val headWalk = headTreeId?.let { TreeWalk.forPath(repository, path, it) }
            if (headWalk == null) {
                // Not in HEAD (new file staged pre-first-commit, or added-but-never-
                // committed): unstaging drops it from the index, leaving it untracked.
                editor.add(DirCacheEditor.DeletePath(path))
            } else {
                headWalk.use { tw ->
                    val mode = tw.getFileMode(0)
                    val objectId = tw.getObjectId(0)
                    editor.add(object : DirCacheEditor.PathEdit(path) {
                        override fun apply(ent: DirCacheEntry) {
                            ent.fileMode = mode
                            ent.setObjectId(objectId)
                            // Smudge the stat info like `git reset --mixed` does on real
                            // git, so a later status always re-hashes this path instead of
                            // trusting size/mtime that may no longer match reality.
                            ent.length = 0
                            ent.lastModified = 0
                        }
                    })
                }
            }
            editor.commit()
            success = true
        } finally {
            if (!success) dc.unlock()
        }
    }

    /**
     * Stage everything: new + modified files, then deletions (git add -u).
     *
     * JGit's `AddCommand` splits this into two calls by design: a plain `add "."` stages
     * new + modified files but not deletions, and `setUpdate(true)` stages modified +
     * deleted tracked files but not new ones — each does its own working-tree walk, and
     * there's no single-call JGit API that covers all three. What we *can* cut is the
     * second call when it can't possibly do anything: if the last known status has no
     * tracked deletions, skip straight past it instead of paying for an empty walk.
     */
    suspend fun stageAll(): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock {
            val hadDeletions = lastStatus?.changes.orEmpty()
                .any { it.unstaged == GitWorkingState.DELETED }
            git.add().addFilepattern(".").call()
            if (hadDeletions) {
                git.add().addFilepattern(".").setUpdate(true).call()
            }
        }
        Unit
    }

    suspend fun unstageAll(): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { git.reset().setMode(ResetCommand.ResetType.MIXED).call() }
        Unit
    }

    /**
     * Discard a working-tree change: reverts a tracked file back to HEAD, or deletes an
     * untracked file outright. Never touches the index (caller ensures it's unstaged first).
     */
    suspend fun discard(path: String, isUntracked: Boolean): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock {
            if (isUntracked) {
                File(workTree, path).deleteRecursively()
            } else {
                git.checkout().addPath(path.normalized()).call()
            }
        }
        Unit
    }

    // ---- reset ---------------------------------------------------------------

    /**
     * Move HEAD (and, per [mode], the index/working tree) to [ref] — "HEAD",
     * "origin/main", "upstream/main" or a bare/short commit id all resolve fine.
     */
    suspend fun resetTo(ref: String, mode: GitResetMode): GitResult<Unit> = ioOp(TITLE_RESET) {
        val target = ref.trim()
        require(target.isNotEmpty()) { "Reset target is empty." }
        mutex.withLock {
            if (repository.resolve(target) == null) {
                throw IllegalArgumentException("Can't resolve '$target'.")
            }
            git.reset().setRef(target).setMode(mode.toJgit()).call()
        }
        Unit
    }

    private fun GitResetMode.toJgit(): ResetCommand.ResetType = when (this) {
        GitResetMode.SOFT -> ResetCommand.ResetType.SOFT
        GitResetMode.MIXED -> ResetCommand.ResetType.MIXED
        GitResetMode.HARD -> ResetCommand.ResetType.HARD
    }

    // ---- commit ------------------------------------------------------------

    suspend fun commit(message: String, amend: Boolean): GitResult<GitCommitSummary> =
        ioOp(TITLE_COMMIT) {
            mutex.withLock {
                val identity = resolveIdentity() ?: throw GitIdentityMissingException()
                val commit = git.commit()
                    .setMessage(message)
                    .setAmend(amend)
                    // JGit allows empty commits by default (unlike the git CLI), so with
                    // nothing staged this would otherwise silently create an empty commit
                    // instead of throwing EmptyCommitException like GitViewModel expects.
                    // Amend is exempt: a message-only reword has no tree change either and
                    // must still go through.
                    .setAllowEmpty(amend)
                    .setAuthor(identity.name, identity.email)
                    .setCommitter(identity.name, identity.email)
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

    /**
     * M8: [force] pushes with a force-with-lease style check: the remote tip is
     * compared against our stored remote-tracking ref first; if the remote has
     * commits we never fetched, we refuse instead of clobbering.
     */
    suspend fun push(
        remote: String,
        credentials: CredentialsProvider?,
        force: Boolean = false,
        onProgress: (GitProgress) -> Unit = {},
    ): GitResult<Unit> = ioOp(TITLE_PUSH) {
        mutex.withLock {
            if (force) {
                val branch = currentBranchName()
                    ?: throw IllegalStateException("Detached HEAD: force push needs a branch.")
                val remoteTip = runCatching {
                    git.lsRemote()
                        .setRemote(remote)
                        .setCredentialsProvider(credentials)
                        .call()
                        .firstOrNull { it.name == "refs/heads/$branch" }
                        ?.objectId
                }.getOrNull()
                val stored = repository.resolve("refs/remotes/$remote/$branch")
                if (remoteTip != null && stored != null && remoteTip != stored) {
                    throw IllegalStateException(
                        "'$remote/$branch' has commits you have not fetched. Fetch first, then force push.",
                    )
                }
            }
            val cmd = git.push()
                .setRemote(remote)
                .setCredentialsProvider(credentials)
                .setProgressMonitor(gitProgressMonitor(onProgress))
            if (force) cmd.setForce(true)
            cmd.call()
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
        mutex.withLock { readRemotes() }
    }

    private fun readRemotes(): List<GitRemoteInfo> =
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

    // ---- M8: branches (unborn-aware) ----------------------------------------

    private fun currentBranchName(): String? =
        runCatching { repository.fullBranch }.getOrNull()
            ?.takeIf { it.startsWith("refs/heads/") }
            ?.removePrefix("refs/heads/")

    /** Local branches + current unborn branch (empty repo me bhi HEAD wali dikhe). */
    suspend fun listBranchesDetailed(): GitResult<List<GitBranchDetail>> = ioOp(TITLE_BRANCH) {
        mutex.withLock {
            val current = currentBranchName()
            val listed = git.branchList().call().map { ref ->
                val name = ref.name.removePrefix("refs/heads/")
                val track = runCatching { trackingInfo(name) }.getOrNull()
                GitBranchDetail(
                    name = name,
                    isCurrent = name == current,
                    upstream = track?.let { "${it.remote}/${it.branch}" },
                    ahead = track?.ahead ?: 0,
                    behind = track?.behind ?: 0,
                )
            }
            // Unborn branch: branchList() is empty but HEAD already points at one.
            if (listed.none { it.isCurrent } && current != null) {
                listed + GitBranchDetail(current, isCurrent = true, upstream = null, ahead = 0, behind = 0)
            } else {
                listed
            }
        }
    }

    suspend fun createBranch(name: String, checkout: Boolean): GitResult<Unit> = ioOp(TITLE_BRANCH) {
        val n = name.trim()
        require(n.isNotEmpty()) { "Branch name is empty." }
        mutex.withLock {
            if (repository.resolve("HEAD") == null) {
                // Unborn: branch has no tip yet, just point HEAD at the new name.
                repository.updateRef(Constants.HEAD).link(Constants.R_HEADS + n)
            } else {
                git.branchCreate().setName(n).call()
                if (checkout) git.checkout().setName(n).call()
            }
        }
        Unit
    }

    suspend fun checkoutBranch(name: String, create: Boolean): GitResult<Unit> = ioOp(TITLE_BRANCH) {
        val n = name.trim()
        mutex.withLock {
            if (create && repository.resolve("HEAD") == null) {
                repository.updateRef(Constants.HEAD).link(Constants.R_HEADS + n)
            } else {
                git.checkout().setName(n).setCreateBranch(create).call()
            }
        }
        Unit
    }

    suspend fun renameBranch(oldName: String?, newName: String): GitResult<Unit> = ioOp(TITLE_BRANCH) {
        val nn = newName.trim()
        require(nn.isNotEmpty()) { "Branch name is empty." }
        mutex.withLock {
            val old = oldName?.trim()?.takeIf { it.isNotEmpty() } ?: currentBranchName()
                ?: throw IllegalStateException("No current branch to rename.")
            if (repository.resolve("HEAD") == null) {
                // Unborn rename: move HEAD symref, drop the stale (tip-less) ref.
                runCatching {
                    val staleRef = repository.updateRef(Constants.R_HEADS + old)
                    staleRef.setForceUpdate(true)
                    staleRef.delete()
                }
                repository.updateRef(Constants.HEAD).link(Constants.R_HEADS + nn)
            } else {
                git.branchRename().setOldName(old).setNewName(nn).call()
            }
        }
        Unit
    }

    suspend fun deleteBranch(name: String, force: Boolean): GitResult<Unit> = ioOp(TITLE_BRANCH) {
        val n = name.trim()
        if (n == currentBranchName()) throw IllegalStateException("Cannot delete the current branch.")
        mutex.withLock { git.branchDelete().setBranchNames(n).setForce(force).call() }
        Unit
    }

    // ---- M8: history + cherry-pick --------------------------------------------

    suspend fun log(max: Int = 300, path: String? = null): GitResult<List<GitCommitSummary>> =
        ioOp(TITLE_LOG) {
            mutex.withLock {
                if (repository.resolve("HEAD") == null) return@ioOp emptyList()
                val cmd = git.log().setMaxCount(max)
                if (path != null) cmd.addPath(path.normalized())
                cmd.call().map { it.toSummary() }.toList()
            }
        }

    /** History search: message / author / hash-prefix filter over a capped walk. */
    suspend fun searchLog(
        query: String,
        mode: GitLogSearchMode,
        max: Int = 300,
    ): GitResult<List<GitCommitSummary>> = ioOp(TITLE_LOG) {
        mutex.withLock {
            if (repository.resolve("HEAD") == null) return@ioOp emptyList()
            val q = query.trim()
            git.log().setMaxCount(3000).call()
                .asSequence()
                .map { it.toSummary() }
                .filter {
                    when (mode) {
                        GitLogSearchMode.MESSAGE -> it.message.contains(q, ignoreCase = true)
                        GitLogSearchMode.AUTHOR -> it.author.contains(q, ignoreCase = true)
                        GitLogSearchMode.HASH ->
                            it.id.startsWith(q, ignoreCase = true) ||
                                it.shortId.startsWith(q, ignoreCase = true)
                    }
                }
                .take(max)
                .toList()
        }
    }

    suspend fun cherryPick(commitId: String): GitResult<Unit> = ioOp(TITLE_CHERRY_PICK) {
        mutex.withLock {
            val result = git.cherryPick()
                .include(repository.resolve(commitId.trim()))
                .call()
            if (result.status != org.eclipse.jgit.api.CherryPickResult.CherryPickStatus.OK) {
                throw IllegalStateException(
                    "Cherry-pick finished with status ${result.status}. Resolve conflicts in the drawer.",
                )
            }
        }
        Unit
    }

    private fun org.eclipse.jgit.revwalk.RevCommit.toSummary() = GitCommitSummary(
        id = name,
        shortId = abbreviate(7).name(),
        message = fullMessage,
        author = authorIdent.name,
        timeMs = authorIdent.`when`.time,
    )

    // ---- M8: stash --------------------------------------------------------------

    suspend fun stashCreate(message: String?): GitResult<Unit> = ioOp(TITLE_STASH) {
        mutex.withLock {
            val cmd = git.stashCreate()
            if (!message.isNullOrBlank()) cmd.setWorkingDirectoryMessage(message.trim())
            cmd.call()
        }
        Unit
    }

    suspend fun stashList(): GitResult<List<GitStashInfo>> = ioOp(TITLE_STASH) {
        mutex.withLock {
            git.stashList().call().toList().mapIndexed { index, c ->
                GitStashInfo(
                    ref = "stash@{$index}",
                    index = index,
                    message = c.shortMessage,
                    timeMs = c.authorIdent.`when`.time,
                )
            }
        }
    }

    suspend fun stashApply(ref: String, pop: Boolean): GitResult<Unit> = ioOp(TITLE_STASH) {
        mutex.withLock {
            git.stashApply().setStashRef(ref).call()
            if (pop) git.stashDrop().setStashRef(stashIndexFromRef(ref)).call()
        }
        Unit
    }

    suspend fun stashDrop(ref: String): GitResult<Unit> = ioOp(TITLE_STASH) {
        mutex.withLock { git.stashDrop().setStashRef(stashIndexFromRef(ref)).call() }
        Unit
    }

    /** JGit's StashDropCommand wants the numeric index, not the "stash@{N}" ref string. */
    private fun stashIndexFromRef(ref: String): Int =
        Regex("""\{(\d+)\}""").find(ref)?.groupValues?.get(1)?.toIntOrNull()
            ?: ref.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid stash ref: $ref")

    // ---- M8: tags ----------------------------------------------------------------

    suspend fun listTags(): GitResult<List<GitTagInfo>> = ioOp(TITLE_TAG) {
        mutex.withLock {
            val walk = RevWalk(repository)
            try {
                git.tagList().call().map { ref ->
                    val peeled = runCatching { repository.refDatabase.peel(ref) }.getOrNull()
                    val commitId = peeled?.peeledObjectId ?: ref.objectId
                    val commit = runCatching { walk.parseCommit(commitId) }.getOrNull()
                    GitTagInfo(
                        name = ref.name.removePrefix("refs/tags/"),
                        commitId = commitId.name,
                        message = commit?.shortMessage,
                        timeMs = commit?.authorIdent?.`when`?.time ?: 0L,
                    )
                }
            } finally {
                walk.close()
            }
        }
    }

    suspend fun createTag(name: String, message: String?): GitResult<Unit> = ioOp(TITLE_TAG) {
        val n = name.trim()
        require(n.isNotEmpty()) { "Tag name is empty." }
        mutex.withLock {
            val cmd = git.tag().setName(n)
            if (!message.isNullOrBlank()) cmd.setAnnotated(true).setMessage(message.trim())
            cmd.call()
        }
        Unit
    }

    suspend fun deleteTag(name: String): GitResult<Unit> = ioOp(TITLE_TAG) {
        mutex.withLock { git.tagDelete().setTags(name.trim()).call() }
        Unit
    }

    // ---- M8: remotes ---------------------------------------------------------------

    suspend fun addRemote(name: String, url: String): GitResult<Unit> = ioOp(TITLE_REMOTE) {
        mutex.withLock {
            git.remoteAdd().setName(name.trim()).setUri(URIish(url.trim())).call()
        }
        Unit
    }

    suspend fun setRemoteUrl(name: String, url: String): GitResult<Unit> = ioOp(TITLE_REMOTE) {
        mutex.withLock {
            repository.config.setString(
                ConfigConstants.CONFIG_REMOTE_SECTION, name, ConfigConstants.CONFIG_KEY_URL, url.trim(),
            )
            repository.config.save()
        }
        Unit
    }

    suspend fun renameRemote(oldName: String, newName: String): GitResult<Unit> = ioOp(TITLE_REMOTE) {
        val old = oldName.trim()
        val new = newName.trim()
        mutex.withLock {
            val cfg = repository.config
            val url = cfg.getString(
                ConfigConstants.CONFIG_REMOTE_SECTION, old, ConfigConstants.CONFIG_KEY_URL,
            ) ?: throw IllegalStateException("Remote '$old' does not exist.")
            val fetch = cfg.getString(ConfigConstants.CONFIG_REMOTE_SECTION, old, "fetch")
            cfg.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, old)
            cfg.setString(ConfigConstants.CONFIG_REMOTE_SECTION, new, ConfigConstants.CONFIG_KEY_URL, url)
            if (fetch != null) {
                cfg.setString(
                    ConfigConstants.CONFIG_REMOTE_SECTION, new, "fetch",
                    fetch.replace("refs/remotes/$old/", "refs/remotes/$new/"),
                )
            }
            cfg.save()
            // Move remote-tracking refs so ahead/behind keeps working after rename.
            val prefix = "refs/remotes/$old/"
            repository.refDatabase.getRefsByPrefix(prefix).forEach { ref ->
                val moved = repository.updateRef("refs/remotes/$new/" + ref.name.removePrefix(prefix))
                moved.setNewObjectId(ref.objectId)
                moved.setForceUpdate(true)
                moved.update()
                val stale = repository.updateRef(ref.name)
                stale.setForceUpdate(true)
                stale.delete()
            }
        }
        Unit
    }

    suspend fun removeRemote(name: String): GitResult<Unit> = ioOp(TITLE_REMOTE) {
        mutex.withLock { git.remoteRemove().setRemoteName(name.trim()).call() }
        Unit
    }

    // ---- M10: merge + conflict resolution ------------------------------------

    /**
     * Merge [branch] into HEAD. Accepts a local short name ("feature"), a
     * remote-tracking path ("origin/feature"), or a tracked branch's short name.
     * Fast-forwards when possible, commits the merge otherwise; stops with
     * [MergeOutcome.CONFLICTS] (MERGE_HEAD written) when the trees clash.
     */
    suspend fun mergeBranch(branch: String): GitResult<MergeOutcome> = ioOp(TITLE_MERGE) {
        val name = branch.trim()
        require(name.isNotEmpty()) { "Branch name is empty." }
        mutex.withLock {
            val commitId = resolveBranchRef(name)
            if (currentBranchName() == name) {
                throw IllegalStateException("Already on '$name'.")
            }
            val result = git.merge()
                .include(commitId)
                .setFastForward(MergeCommand.FastForwardMode.FF)
                .setCommit(true)
                .call()
            when (result.mergeStatus) {
                MergeResult.MergeStatus.FAST_FORWARD -> MergeOutcome.FAST_FORWARD
                MergeResult.MergeStatus.MERGED -> MergeOutcome.MERGED
                MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> MergeOutcome.ALREADY_UP_TO_DATE
                MergeResult.MergeStatus.CONFLICTING -> MergeOutcome.CONFLICTS
                else -> throw IllegalStateException(
                    "Merge finished with status ${result.mergeStatus}.",
                )
            }
        }
    }

    /**
     * Resolves [name] the same way for merge and rebase targets: a local short name
     * ("feature"), a remote-tracking path ("origin/feature"), or a tracked branch's
     * short name (falls back to its configured remote ref). Call only under [mutex].
     */
    private fun resolveBranchRef(name: String): ObjectId =
        repository.resolve("refs/heads/$name")
            ?: if (name.contains('/')) repository.resolve("refs/remotes/$name") else null
            ?: run {
                // Tracked branch: fall back to its configured remote ref.
                val remote = repository.config.getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, name,
                    ConfigConstants.CONFIG_KEY_REMOTE,
                )
                val merge = repository.config.getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, name,
                    ConfigConstants.CONFIG_KEY_MERGE,
                )
                if (remote != null && merge != null) {
                    repository.resolve(
                        "refs/remotes/$remote/${merge.removePrefix("refs/heads/")}",
                    )
                } else null
            }
            ?: throw IllegalStateException("Cannot resolve branch '$name'.")

    /** Local + remote short names minus the current branch, for the merge picker. */
    suspend fun mergeCandidates(): GitResult<List<String>> = ioOp(TITLE_BRANCH) {
        mutex.withLock {
            val current = currentBranchName()
            val local = git.branchList().call()
                .map { it.name.removePrefix("refs/heads/") }
            val remote = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                .map { it.name.removePrefix("refs/remotes/") }
                .filter { !it.endsWith("/HEAD") }
            (local + remote).filter { it != current }.distinct().sorted()
        }
    }

    /** Saved MERGE_MSG content, to prefill the "Complete merge" dialog. */
    suspend fun mergeMessage(): GitResult<String?> = ioOp(TITLE_MERGE) {
        mutex.withLock {
            val f = File(repository.directory, "MERGE_MSG")
            if (f.isFile) f.readText().trim().ifBlank { null } else null
        }
    }

    /** Plan-solved abort: drop the merge state files, then hard-reset the tree. */
    suspend fun abortMerge(): GitResult<Unit> = ioOp(TITLE_MERGE) {
        mutex.withLock {
            if (repository.readMergeHeads() == null) return@ioOp Unit
            repository.writeMergeCommitMsg(null)
            repository.writeMergeHeads(null)
            git.reset().setMode(ResetCommand.ResetType.HARD).call()
        }
        Unit
    }

    /** Commit the merge once every conflicted path is staged. */
    suspend fun completeMerge(message: String): GitResult<GitCommitSummary> =
        ioOp(TITLE_MERGE) {
            val msg = message.trim()
            require(msg.isNotEmpty()) { "Commit message is empty." }
            mutex.withLock {
                if (repository.readMergeHeads() == null) {
                    throw IllegalStateException("No merge is in progress.")
                }
                val identity = resolveIdentity() ?: throw GitIdentityMissingException()
                git.commit()
                    .setMessage(msg)
                    .setAuthor(identity.name, identity.email)
                    .setCommitter(identity.name, identity.email)
                    .call()
                    .toSummary()
            }
        }

    /** File-level resolution (ACSIDE pattern): checkout one stage, then stage it. */
    suspend fun checkoutConflictSide(
        path: String,
        side: GitConflictSide,
    ): GitResult<Unit> = ioOp(TITLE_MERGE) {
        val p = path.normalized()
        mutex.withLock {
            git.checkout()
                .setStage(
                    if (side == GitConflictSide.OURS) CheckoutCommand.Stage.OURS
                    else CheckoutCommand.Stage.THEIRS,
                )
                .addPath(p)
                .call()
            git.add().addFilepattern(p).call()
        }
        Unit
    }

    /** "Mark resolved" = keep the file exactly as edited and stage it (plan 3.4). */
    suspend fun markConflictResolved(path: String): GitResult<Unit> = ioOp(TITLE_STAGE) {
        mutex.withLock { git.add().addFilepattern(path.normalized()).call() }
        Unit
    }

    /** Stage-2 (ours) / stage-3 (theirs) content of a conflicted path, for preview. */
    suspend fun conflictSideContent(
        path: String,
        side: GitConflictSide,
    ): GitResult<String> = ioOp(TITLE_DIFF) {
        val p = path.normalized()
        mutex.withLock {
            val stage = if (side == GitConflictSide.OURS) {
                DirCacheEntry.STAGE_2
            } else {
                DirCacheEntry.STAGE_3
            }
            val dc = repository.readDirCache()
            val firstIdx = dc.findEntry(p)
            if (firstIdx < 0) {
                throw IllegalStateException("No ${side.name.lowercase()} version found for $p.")
            }
            val nextIdx = dc.nextEntry(firstIdx)
            val entry = (firstIdx until nextIdx)
                .map { dc.getEntry(it) }
                .firstOrNull { it.stage == stage }
                ?: throw IllegalStateException(
                    "No ${side.name.lowercase()} version found for $p.",
                )
            val loader = repository.open(entry.objectId)
            String(loader.cachedBytes, StandardCharsets.UTF_8)
        }
    }

    // ---- M11: rebase ----------------------------------------------------------

    /**
     * Rebase the current branch onto [branch] (same name resolution as [mergeBranch]).
     * Stops with [RebaseOutcome.CONFLICTS] (REBASE_MERGE state written) when a patch
     * fails to apply; resolve conflicted paths the same way as a merge conflict, then
     * call [continueRebase] (or [skipRebaseCommit] / [abortRebase]).
     */
    suspend fun rebaseOnto(branch: String): GitResult<RebaseOutcome> = ioOp(TITLE_REBASE) {
        val name = branch.trim()
        require(name.isNotEmpty()) { "Rebase target is empty." }
        mutex.withLock {
            val onto = resolveBranchRef(name)
            if (currentBranchName() == name) {
                throw IllegalStateException("Already on '$name'.")
            }
            git.rebase().setUpstream(onto).call().status.toOutcome()
        }
    }

    /** Resumes a stopped rebase once every conflicted path is resolved and staged. */
    suspend fun continueRebase(): GitResult<RebaseOutcome> = ioOp(TITLE_REBASE) {
        mutex.withLock {
            if (!repository.repositoryState.isRebasing) {
                throw IllegalStateException("No rebase is in progress.")
            }
            git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call().status.toOutcome()
        }
    }

    /** Drops the commit currently being replayed and moves on to the next one. */
    suspend fun skipRebaseCommit(): GitResult<RebaseOutcome> = ioOp(TITLE_REBASE) {
        mutex.withLock {
            if (!repository.repositoryState.isRebasing) {
                throw IllegalStateException("No rebase is in progress.")
            }
            git.rebase().setOperation(RebaseCommand.Operation.SKIP).call().status.toOutcome()
        }
    }

    /** Restores the branch to where the rebase started; conflict resolutions are lost. */
    suspend fun abortRebase(): GitResult<Unit> = ioOp(TITLE_REBASE) {
        mutex.withLock {
            if (!repository.repositoryState.isRebasing) return@ioOp Unit
            git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
        }
        Unit
    }

    private fun RebaseResult.Status.toOutcome(): RebaseOutcome = when (this) {
        RebaseResult.Status.FAST_FORWARD -> RebaseOutcome.FAST_FORWARD
        RebaseResult.Status.OK -> RebaseOutcome.OK
        RebaseResult.Status.UP_TO_DATE -> RebaseOutcome.ALREADY_UP_TO_DATE
        RebaseResult.Status.STOPPED, RebaseResult.Status.CONFLICTS -> RebaseOutcome.CONFLICTS
        RebaseResult.Status.NOTHING_TO_COMMIT -> throw IllegalStateException(
            "Nothing left to commit for this step — try Skip instead.",
        )
        else -> throw IllegalStateException("Rebase finished with status $this.")
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
            mergeInProgress = runCatching { repository.readMergeHeads() != null }
                .getOrDefault(false),
            rebaseInProgress = runCatching { repository.repositoryState.isRebasing }
                .getOrDefault(false),
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

    private fun doStatus(): GitWorkingTreeStatus = buildStatus().also { lastStatus = it }

    private fun buildStatus(): GitWorkingTreeStatus {
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
        // Conflicting paths dono maps me hote hain (staged + unstaged), isliye distinct()
        // zaroori hai — warna wahi path do baar GitPathChange me aa jaata hai aur
        // LazyColumn ka "staged:<path>" key duplicate ho kar crash karta hai.
        val rawAll = staged.keys + unstaged.keys
        val all = rawAll.distinct().sorted()
        if (all.size != rawAll.size) {
            val dupes = rawAll.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            android.util.Log.w("XcodeGit", "doStatus: duplicate repoRelativePath(s) found: $dupes")
        }
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
                GitResult.Ok(runWithLockRecovery(block))
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepositoryNotFoundException) {
                GitResult.Err(GitErrorFactory.from(e, title) { "This folder is not a Git repository." })
            } catch (e: Throwable) {
                GitResult.Err(GitErrorFactory.from(e, title) { it.message ?: "Unknown error" })
            }
        }

    /**
     * Runs [block] once; if it fails on a leftover `.lock` file (e.g. the app was killed
     * mid-write on a previous run), removes the stale file and retries exactly once. Safe
     * because [mutex] already rules out a live, in-process writer holding that lock.
     *
     * JGit's higher-level commands (CommitCommand, AddCommand, PushCommand, ...) catch the
     * [LockFailedException] thrown by the low-level lock/write code and rethrow it wrapped in a
     * [org.eclipse.jgit.api.errors.JGitInternalException] ("Exception caught during execution of
     * ... command"), with the original preserved only as [Throwable.cause]. So this must search
     * the whole cause chain, not just check the outermost exception's type, or recovery silently
     * never triggers for any command that wraps its failures this way.
     */
    private suspend fun <T> runWithLockRecovery(block: suspend () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val lockFailure = e.findLockFailedException()
            if (lockFailure != null && clearStaleLock(lockFailure)) block() else throw e
        }

    /** Walks [this]'s cause chain looking for a [LockFailedException]. */
    private tailrec fun Throwable.findLockFailedException(): LockFailedException? = when {
        this is LockFailedException -> this
        cause != null && cause !== this -> cause!!.findLockFailedException()
        else -> null
    }

    /**
     * Deletes the stale `.lock` sidecar for the file JGit failed to lock. [LockFailedException]
     * reports the file it was locking (e.g. `.git/index`, `.git/HEAD`, a packed-refs file, a ref
     * under `.git/refs/...`), not the lock file itself — the lock file is always that path plus
     * a literal `.lock` suffix, regardless of which git operation created it.
     */
    private fun clearStaleLock(e: LockFailedException): Boolean {
        val lockFile = runCatching { File(e.file.path + ".lock") }.getOrNull() ?: return false
        return lockFile.exists() && lockFile.delete()
    }

    /** Windows-style separators must never reach addFilepattern/addPath (plan section 3.4). */
    private fun String.normalized(): String = replace(File.separatorChar, '/')

    companion object {
        /** Shares one [Mutex] per canonical repo path across every [GitSession] instance pointed at it. */
        private val mutexRegistry = ConcurrentHashMap<String, Mutex>()

        private const val TITLE_STATUS = "Git status"
        private const val TITLE_STAGE = "Git stage"
        private const val TITLE_COMMIT = "Git commit"
        private const val TITLE_PUSH = "Git push"
        private const val TITLE_PULL = "Git pull"
        private const val TITLE_FETCH = "Git fetch"
        private const val TITLE_IDENTITY = "Git identity"
        private const val TITLE_DIFF = "Git diff"
        private const val TITLE_BRANCH = "Git branch"
        private const val TITLE_LOG = "Git history"
        private const val TITLE_CHERRY_PICK = "Git cherry-pick"
        private const val TITLE_STASH = "Git stash"
        private const val TITLE_TAG = "Git tag"
        private const val TITLE_REMOTE = "Git remote"
        private const val TITLE_INIT = "Initialize repository"
        private const val TITLE_MERGE = "Git merge"
        private const val TITLE_REBASE = "Git rebase"
        private const val TITLE_RESET = "Git reset"
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
