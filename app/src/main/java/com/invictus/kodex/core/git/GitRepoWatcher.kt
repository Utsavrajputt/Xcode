package com.invictus.kodex.core.git

import android.os.FileObserver
import java.io.File

/**
 * Watches a repo's `.git` directory for changes made outside [GitSession] — e.g. `git`
 * run from Termux, or another app — so the UI can refresh live instead of only on a
 * manual pull-to-refresh. [onChanged] fires on a background thread, may fire in bursts (a
 * single `git pull` touches HEAD, the index and several refs one after another), and the
 * caller is expected to debounce.
 *
 * Only the paths that actually affect status/branch/tracking are watched: `.git` itself
 * (HEAD, index, MERGE_HEAD/ORIG_HEAD/CHERRY_PICK_HEAD, packed-refs, and the rebase-merge /
 * rebase-apply / sequencer directories appearing or disappearing), plus everything under
 * `refs/` — recursively, since branch and tag names may contain slashes and every remote
 * gets its own subfolder, and [FileObserver] itself only ever watches one directory.
 * `.git/objects` and `.git/logs` are deliberately left unwatched: every commit touches
 * them regardless of who made it, so watching them would just mean firing (and debouncing)
 * on our own writes too.
 */
class GitRepoWatcher(private val onChanged: () -> Unit) {

    private val observers = HashMap<String, FileObserver>()

    /** Stops any previous watch and starts watching [gitDir]. No-op if it isn't a repo. */
    @Synchronized
    fun start(gitDir: File) {
        stop()
        if (!gitDir.isDirectory) return
        watch(gitDir, recurse = false)
        watch(File(gitDir, "refs"), recurse = true)
    }

    @Synchronized
    fun stop() {
        observers.values.forEach { it.stopWatching() }
        observers.clear()
    }

    /** Registers [dir]; if [recurse], also descends into every subdirectory under it. */
    private fun watch(dir: File, recurse: Boolean) {
        if (!dir.isDirectory || dir.path in observers) return
        val observer = object : FileObserver(dir, MASK) {
            override fun onEvent(event: Int, path: String?) {
                onChanged()
                if (!recurse || path == null) return
                when (event and FileObserver.ALL_EVENTS) {
                    FileObserver.CREATE, FileObserver.MOVED_TO -> {
                        val child = File(dir, path)
                        if (child.isDirectory) watch(child, recurse = true)
                    }
                    FileObserver.DELETE_SELF, FileObserver.MOVE_SELF -> forget(dir.path)
                }
            }
        }
        observer.startWatching()
        observers[dir.path] = observer
        if (recurse) {
            dir.listFiles { f -> f.isDirectory }?.forEach { watch(it, recurse = true) }
        }
    }

    /** [path] just vanished (renamed/deleted) — drop its watcher and everything below it. */
    @Synchronized
    private fun forget(path: String) {
        val below = path + File.separator
        observers.keys.filter { it == path || it.startsWith(below) }
            .forEach { observers.remove(it)?.stopWatching() }
    }

    private companion object {
        const val MASK = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or
            FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    }
}
