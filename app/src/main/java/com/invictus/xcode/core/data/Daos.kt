package com.invictus.xcode.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.invictus.xcode.core.project.PathUtil
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecentProjectDao {
    @Query("SELECT * FROM recent_projects ORDER BY lastOpenedAt DESC")
    abstract fun observeAll(): Flow<List<RecentProjectEntity>>

    @Query("SELECT * FROM recent_projects ORDER BY lastOpenedAt DESC LIMIT 1")
    abstract suspend fun latest(): RecentProjectEntity?

    @Query("SELECT * FROM recent_projects")
    abstract suspend fun getAll(): List<RecentProjectEntity>

    @Upsert
    abstract suspend fun upsert(item: RecentProjectEntity)

    @Upsert
    abstract suspend fun upsertAll(items: List<RecentProjectEntity>)

    @Delete
    abstract suspend fun deleteAll(items: List<RecentProjectEntity>)

    @Query("DELETE FROM recent_projects WHERE path = :path")
    abstract suspend fun deleteByPath(path: String)

    /** Keeps only the [keep] most recently opened entries. */
    @Query(
        "DELETE FROM recent_projects WHERE path NOT IN " +
            "(SELECT path FROM recent_projects ORDER BY lastOpenedAt DESC LIMIT :keep)",
    )
    abstract suspend fun trim(keep: Int)

    /** A folder moved: fix every recent at or below it. */
    @Transaction
    open suspend fun remap(old: String, new: String) {
        val affected = getAll().filter { PathUtil.isSameOrUnder(it.path, old) }
        if (affected.isEmpty()) return
        deleteAll(affected)
        upsertAll(affected.map { it.copy(path = PathUtil.rebase(it.path, old, new)) })
    }
}

@Dao
abstract class PinDao {
    @Query("SELECT * FROM pins WHERE projectPath = :projectPath ORDER BY pinnedAt ASC")
    abstract fun observe(projectPath: String): Flow<List<PinEntity>>

    @Query("SELECT * FROM pins")
    abstract suspend fun getAll(): List<PinEntity>

    @Upsert
    abstract suspend fun upsert(pin: PinEntity)

    @Upsert
    abstract suspend fun upsertAll(pins: List<PinEntity>)

    @Delete
    abstract suspend fun deleteAll(pins: List<PinEntity>)

    @Query("DELETE FROM pins WHERE projectPath = :projectPath AND path = :path")
    abstract suspend fun delete(projectPath: String, path: String)

    /** A file or folder moved: fix pins on it (or below it), and pins of a project that moved. */
    @Transaction
    open suspend fun remap(old: String, new: String) {
        val affected = getAll().filter {
            PathUtil.isSameOrUnder(it.path, old) || PathUtil.isSameOrUnder(it.projectPath, old)
        }
        if (affected.isEmpty()) return
        deleteAll(affected)
        upsertAll(
            affected.map {
                it.copy(
                    path = if (PathUtil.isSameOrUnder(it.path, old)) PathUtil.rebase(it.path, old, new) else it.path,
                    projectPath = if (PathUtil.isSameOrUnder(it.projectPath, old)) {
                        PathUtil.rebase(it.projectPath, old, new)
                    } else {
                        it.projectPath
                    },
                )
            },
        )
    }

    /** A file or folder was deleted: drop pins on it and below it. */
    @Transaction
    open suspend fun deleteUnder(path: String) {
        val affected = getAll().filter { PathUtil.isSameOrUnder(it.path, path) }
        if (affected.isNotEmpty()) deleteAll(affected)
    }
}
