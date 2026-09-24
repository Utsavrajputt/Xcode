package com.invictus.xcode.core.fs

import android.os.FileObserver
import java.io.File

/**
 * Watches a set of directories (the expanded ones) for entries being created, deleted or
 * moved, so the tree can refresh when something outside the app (Termux, Git, another app)
 * changes them. [onDirectoryChanged] fires on a background thread and may fire in bursts;
 * the caller is expected to debounce.
 *
 * Note: on Android 11+ shared storage sits behind FUSE and inotify can miss changes made
 * by other apps, so callers should also refresh on resume.
 */
class DirectoryWatcher(private val onDirectoryChanged: (File) -> Unit) {

    private val observers = HashMap<String, FileObserver>()

    @Synchronized
    fun setWatched(dirs: Collection<File>) {
        val wanted = dirs.associateBy { it.path }
        val iterator = observers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) {
                entry.value.stopWatching()
                iterator.remove()
            }
        }
        for ((dirPath, dir) in wanted) {
            if (dirPath in observers) continue
            val observer = object : FileObserver(dir, MASK) {
                override fun onEvent(event: Int, path: String?) {
                    onDirectoryChanged(dir)
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
        const val MASK = FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
            FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    }
}
