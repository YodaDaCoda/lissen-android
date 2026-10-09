package org.grakovne.lissen.content.cache.persistent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.ForkSchema
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which cached chapters the auto-cache itself added, so its clean-up (trim behind the
 * playhead, reclaim on book switch) can never touch a chapter the user downloaded by hand - see
 * [org.grakovne.lissen.content.cache.persistent.ContentAutoCachingService].
 *
 * The table is fork-owned ([ForkSchema]), so it has no Room entity and is reached with plain SQL.
 */
@Singleton
class AutoCacheOwnershipRepository
  @Inject
  constructor(
    private val storage: LocalCacheStorage,
  ) {
    private val db get() = storage.openHelper.writableDatabase

    suspend fun markOwned(
      bookId: String,
      chapterIds: List<String>,
    ) {
      if (chapterIds.isEmpty()) return
      val now = Instant.now().toEpochMilli()

      inTransaction {
        chapterIds.forEach {
          db.execSQL(
            "INSERT OR REPLACE INTO auto_cache_ownership (bookId, bookChapterId, cachedAt) VALUES (?, ?, ?)",
            arrayOf<Any>(bookId, it, now),
          )
        }
      }
    }

    suspend fun clearOwned(
      bookId: String,
      chapterIds: List<String>,
    ) {
      if (chapterIds.isEmpty()) return

      inTransaction {
        chapterIds.forEach {
          db.execSQL("DELETE FROM auto_cache_ownership WHERE bookId = ? AND bookChapterId = ?", arrayOf<Any>(bookId, it))
        }
      }
    }

    suspend fun clearAllOwned(bookId: String) =
      inTransaction { db.execSQL("DELETE FROM auto_cache_ownership WHERE bookId = ?", arrayOf<Any>(bookId)) }

    suspend fun fetchOwnedChapterIds(bookId: String): List<String> =
      query("SELECT bookChapterId FROM auto_cache_ownership WHERE bookId = ?", arrayOf(bookId))

    suspend fun fetchOwnedBookIds(): List<String> = query("SELECT DISTINCT bookId FROM auto_cache_ownership", emptyArray())

    private suspend fun inTransaction(block: () -> Unit) =
      withContext(Dispatchers.IO) { storage.runInTransaction(block) }

    private suspend fun query(
      sql: String,
      args: Array<String>,
    ): List<String> =
      withContext(Dispatchers.IO) {
        db.query(sql, args).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
      }
  }
