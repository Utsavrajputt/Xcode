package com.invictus.xcode.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchHistoryDao {
    @Query("SELECT * FROM search_queries WHERE kind = :kind ORDER BY usedAt DESC LIMIT :limit")
    abstract fun observe(kind: String, limit: Int): Flow<List<SearchQueryEntity>>

    @Transaction
    open suspend fun record(kind: String, query: String, optionsJson: String, keep: Int) {
        val q = query.trim()
        if (q.isEmpty()) return
        deleteByQuery(kind, q) // duplicate nahi: latest use sabse upar
        upsert(
            SearchQueryEntity(
                kind = kind,
                query = q,
                optionsJson = optionsJson,
                usedAt = System.currentTimeMillis(),
            ),
        )
        trim(kind, keep)
    }

    @Upsert
    abstract suspend fun upsert(item: SearchQueryEntity)

    @Query("DELETE FROM search_queries WHERE kind = :kind AND query = :query")
    abstract suspend fun deleteByQuery(kind: String, query: String)

    @Query(
        "DELETE FROM search_queries WHERE kind = :kind AND id NOT IN " +
            "(SELECT id FROM search_queries WHERE kind = :kind ORDER BY usedAt DESC LIMIT :keep)",
    )
    abstract suspend fun trim(kind: String, keep: Int)

    @Query("DELETE FROM search_queries WHERE kind = :kind")
    abstract suspend fun clear(kind: String)
}
