package com.invictus.kodex.core.project

import com.invictus.kodex.core.data.AppDatabase
import com.invictus.kodex.core.data.PinEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

data class RecentProject(val file: File, val lastOpenedAt: Long, val pinned: Boolean = false)

data class PinnedItem(val file: File, val isDirectory: Boolean)

/** Recents and pins, backed by Room. Paths are stored as plain strings. */
class ProjectRepository(db: AppDatabase) {
    private val recentDao = db.recentProjectDao()
    private val pinDao = db.pinDao()

    val recents: Flow<List<RecentProject>> = recentDao.observeAll()
        .map { list -> list.map { RecentProject(File(it.path), it.lastOpenedAt, it.pinned) } }

    suspend fun latestRecent(): File? = recentDao.latest()?.let { File(it.path) }

    suspend fun recordOpened(root: File) {
        recentDao.touch(root.path, System.currentTimeMillis())
        recentDao.trim(MAX_RECENTS)
    }

    /** Only forgets the entry; the folder on disk is never touched. */
    suspend fun removeRecent(path: String) = recentDao.deleteByPath(path)

    suspend fun setRecentPinned(path: String, pinned: Boolean) = recentDao.setPinned(path, pinned)

    /** Call after a project folder was deleted from disk: forgets it and any pins inside it. */
    suspend fun onProjectDeleted(dir: File) {
        recentDao.deleteUnder(dir.path)
        pinDao.deleteUnder(dir.path)
    }

    fun observePins(projectPath: String): Flow<List<PinnedItem>> = pinDao.observe(projectPath)
        .map { list -> list.map { PinnedItem(File(it.path), it.isDirectory) } }

    suspend fun setPinned(projectPath: String, file: File, isDirectory: Boolean, pinned: Boolean) {
        if (pinned) {
            pinDao.upsert(PinEntity(projectPath, file.path, isDirectory, System.currentTimeMillis()))
        } else {
            pinDao.delete(projectPath, file.path)
        }
    }

    /** Call after any rename/move so recents and pins follow the file. */
    suspend fun onPathMoved(old: File, new: File) {
        recentDao.remap(old.path, new.path)
        pinDao.remap(old.path, new.path)
    }

    /** Call after a delete. Recents stay (the sheet shows them as missing); pins go. */
    suspend fun onPathDeleted(file: File) = pinDao.deleteUnder(file.path)

    private companion object {
        const val MAX_RECENTS = 50
    }
}
