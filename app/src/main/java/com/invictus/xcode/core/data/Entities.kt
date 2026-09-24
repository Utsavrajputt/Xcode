package com.invictus.xcode.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A workspace the user opened before. [path] is the project folder. */
@Entity(tableName = "recent_projects")
data class RecentProjectEntity(
    @PrimaryKey val path: String,
    val lastOpenedAt: Long,
)

/** A file/folder pinned to the top of the tree; pins belong to one project. */
@Entity(tableName = "pins", primaryKeys = ["projectPath", "path"])
data class PinEntity(
    val projectPath: String,
    val path: String,
    val isDirectory: Boolean,
    val pinnedAt: Long,
)
