package org.grakovne.lissen.content.cache.persistent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.ForkSchema
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import javax.inject.Inject
import javax.inject.Singleton

data class AutoCacheBook(
  val bookId: String,
  val title: String,
  val lastActiveAt: Long,
)

/**
 * The auto-cached books this device has played: a title to show and when each last played here, so
 * the least recently played can be evicted first. The audio itself lives in the auto-cache.
 *
 * The table is fork-owned ([ForkSchema]), so it has no Room entity and is reached with plain SQL.
 */
@Singleton
class AutoCacheBookRepository
  @Inject
  constructor(
    private val storage: LocalCacheStorage,
  ) {
    private val db get() = storage.openHelper.writableDatabase

    suspend fun touch(
      bookId: String,
      title: String,
      at: Long,
    ) = withContext(Dispatchers.IO) {
      db.execSQL(
        "INSERT OR REPLACE INTO auto_cache_books (bookId, title, lastActiveAt) VALUES (?, ?, ?)",
        arrayOf<Any>(bookId, title, at),
      )
    }

    suspend fun fetchAll(): List<AutoCacheBook> =
      withContext(Dispatchers.IO) {
        db.query("SELECT bookId, title, lastActiveAt FROM auto_cache_books").use { cursor ->
          buildList {
            while (cursor.moveToNext()) add(AutoCacheBook(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
          }
        }
      }

    suspend fun delete(bookId: String) =
      withContext(Dispatchers.IO) { db.execSQL("DELETE FROM auto_cache_books WHERE bookId = ?", arrayOf<Any>(bookId)) }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { db.execSQL("DELETE FROM auto_cache_books") }
  }
