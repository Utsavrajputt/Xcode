package com.invictus.xcode.core.fs

import android.os.FileObserver
import java.io.File

/**
 * Watches the on-disk files behind currently open editor tabs for changes made outside the app
 * (Termux, git, another app). One [FileObserver] per distinct *parent directory* of an open
 * tab -- not one per tab -- so two open files in the same folder share a single observer, same
 * shape as [DirectoryWatcher] for the file tree.
 *
 * Watching the parent dir (not just the file itself) is what plan 7 asks for: some editors
 * replace-on-save (write a temp file, then rename over the original), which the original file's
 * own inode never sees as a MODIFY, only the directory sees as a MOVED_TO/CREATE.
 *
 * [onPathChanged] fires on a background thread with the changed file's absolute path (not
 * necessarily one of [setWatchedFiles]' own paths -- the caller filters). May fire in bursts;
 * callers debounce.
 */
class ExternalChangeWatcher(private val onPathChanged: (String) -> Unit) {

    private val observers = HashMap<String, FileObserver>()

    /** Pass the full set of currently open tab paths; rebuilds only what changed. */
    @Synchronized
    fun setWatchedFiles(paths: Collection<String>) {
        val wantedDirs = paths.mapNotNull { File(it).parentFile }.associateBy { it.path }
        val iterator = observers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wantedDirs) {
                entry.value.stopWatching()
                iterator.remove()
            }
        }
        for ((dirPath, dir) in wantedDirs) {
            if (dirPath in observers || !dir.isDirectory) continue
            val observer = object : FileObserver(dir, MASK) {
                override fun onEvent(event: Int, path: String?) {
                    val name = path ?: return
                    onPathChanged(File(dir, name).path)
                }
            }
            observer.startWatching()
            observers[dirPath] = observer
        }
    }

    @Synchronized
    fun stop() {
        observers.values.forEach { it.stopWatching() }
        observers.clear()
    }

    private companion object {
        const val MASK = FileObserver.MODIFY or FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
    }
}
