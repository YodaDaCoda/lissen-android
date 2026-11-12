package org.grakovne.lissen.content.cache.persistent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The chapters the old whole-file auto-cache stored beside manual downloads, kept only so they can be
 * removed once and the table dropped. Fork-owned table, reached with plain SQL.
 */
@Singleton
class LegacyAutoCacheOwnershipRepository
  @Inject
  constructor(
    private val storage: LocalCacheStorage,
  ) {
    private val db get() = storage.openHelper.writableDatabase

    /** Auto-owned chapter ids by book; empty once the table has been dropped. */
    suspend fun fetchOwned(): Map<String, Set<String>> =
      withContext(Dispatchers.IO) {
        val exists =
          db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'auto_cache_ownership'").use { it.moveToFirst() }
        if (!exists) return@withContext emptyMap()

        db.query("SELECT bookId, bookChapterId FROM auto_cache_ownership").use { cursor ->
          buildMap<String, MutableSet<String>> {
            while (cursor.moveToNext()) getOrPut(cursor.getString(0)) { mutableSetOf() } += cursor.getString(1)
          }
        }
      }

    suspend fun dropTable() = withContext(Dispatchers.IO) { db.execSQL("DROP TABLE IF EXISTS auto_cache_ownership") }
  }
