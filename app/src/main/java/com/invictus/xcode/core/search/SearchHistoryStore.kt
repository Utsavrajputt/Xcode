package com.invictus.xcode.core.search

import com.invictus.xcode.core.data.AppDatabase
import com.invictus.xcode.core.data.SearchQueryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class SearchKind(val key: String) {
    FILE("file"),
    CODE("code"),
}

/** Plan 3.6: dono searches ki alag history, last [KEEP] entries. */
class SearchHistoryStore(private val db: AppDatabase) {
    fun observe(kind: SearchKind, limit: Int = KEEP): Flow<List<SearchQueryEntity>> =
        db.searchHistoryDao().observe(kind.key, limit)

    suspend fun record(kind: SearchKind, query: String, optionsJson: String = "{}") =
        withContext(Dispatchers.IO) { db.searchHistoryDao().record(kind.key, query, optionsJson, KEEP) }

    suspend fun delete(kind: SearchKind, query: String) =
        withContext(Dispatchers.IO) { db.searchHistoryDao().deleteByQuery(kind.key, query) }

    suspend fun clear(kind: SearchKind) =
        withContext(Dispatchers.IO) { db.searchHistoryDao().clear(kind.key) }

    companion object {
        const val KEEP = 50
    }
}
