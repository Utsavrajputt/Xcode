package com.invictus.xcode.core.git

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Shares one [GitSession] - and therefore one JGit [org.eclipse.jgit.lib.Repository], with
 * its already-parsed pack indexes and object caches - across every screen pointed at the same
 * repo, instead of each screen (`GitViewModel`, `GitBranchesViewModel`, `GitHistoryViewModel`,
 * `GitTagsViewModel`, `GitStashViewModel`, `GitRemotesViewModel`, `GitCredentialsViewModel`,
 * `GitOnboardingViewModel`, ...) cold-opening its own on every navigation.
 *
 * [acquire]/[release] are ref-counted per canonical repo path: the underlying [GitSession] is
 * only closed once every caller that acquired it has released it, so it's safe for e.g. both
 * the Source Control screen and the Branches screen to hold it at the same time. Call [acquire]
 * once per owner (a ViewModel's constructor/`init`) and [release] exactly once when that owner
 * is done (`onCleared`) - an unmatched acquire leaks the session for the rest of the process
 * (same as today, just one leak instead of one per screen visit); an unmatched extra release
 * would close the session while another screen is still using it, so every call site must
 * pair its own single acquire with its own single release, never more, never fewer.
 *
 * Every call site in this app passes the same app-wide [globalIdentityFile] (it comes from
 * `AppContainer.gitGlobalIdentityFile`), so keying purely by repo path is safe - the value
 * from whichever caller acquires a path *first* is the one every later caller for that path
 * gets back, since a shared session is a single object, not reconstructed per call.
 */
object GitSessionRegistry {
    private class Entry(val session: GitSession, var refCount: Int)

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun acquire(
        workTree: File,
        globalIdentityFile: File? = null,
        io: CoroutineDispatcher = Dispatchers.IO,
    ): GitSession {
        val key = keyFor(workTree)
        val entry = entries.getOrPut(key) { Entry(GitSession(workTree, globalIdentityFile, io), 0) }
        entry.refCount++
        return entry.session
    }

    /** Must be called exactly once for every [acquire] of [workTree], typically from `onCleared`. */
    @Synchronized
    fun release(workTree: File) {
        val key = keyFor(workTree)
        val entry = entries[key] ?: return
        entry.refCount--
        if (entry.refCount <= 0) {
            entries.remove(key)
            entry.session.close()
        }
    }

    private fun keyFor(workTree: File): String =
        runCatching { workTree.canonicalPath }.getOrDefault(workTree.absolutePath)
}
