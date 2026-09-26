package com.invictus.xcode.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Plan 3.6: search history. kind = "file" | "code"; optionsJson = code-search options snapshot. */
@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val query: String,
    val optionsJson: String,
    val usedAt: Long,
)
