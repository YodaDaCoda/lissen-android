package org.grakovne.lissen.content.cache.persistent

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Tables this fork adds to the cache database, kept out of Room: they are versioned here, by
 * [VERSION_TABLE], and never by Room's own schema version. That version belongs to upstream; two
 * lines of migrations numbered on the same counter cannot both be applied to one database.
 * Room ignores tables it has no entity for, so upstream's schema export and migrations stay untouched.
 *
 * Run from the database's `onOpen`, so every step must be idempotent and the order of [steps] is final:
 * append only.
 */
object ForkSchema {
  private const val VERSION_TABLE = "fork_schema_version"

  private val steps: List<(SupportSQLiteDatabase) -> Unit> =
    listOf(
      // auto-cache ownership: the chapters the auto-cache added itself, cascading with the book
      { db ->
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS auto_cache_ownership (
              bookId TEXT NOT NULL,
              bookChapterId TEXT NOT NULL,
              cachedAt INTEGER NOT NULL,
              PRIMARY KEY (bookId, bookChapterId),
              FOREIGN KEY (bookId) REFERENCES detailed_books(id) ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_cache_ownership_bookId ON auto_cache_ownership(bookId)")
      },
    )

  /** Registered on the cache database builder. */
  val callback =
    object : RoomDatabase.Callback() {
      override fun onOpen(db: SupportSQLiteDatabase) = migrate(db)
    }

  fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS $VERSION_TABLE (version INTEGER NOT NULL)")

    val applied =
      db.query("SELECT COALESCE(MAX(version), 0) FROM $VERSION_TABLE").use { if (it.moveToFirst()) it.getInt(0) else 0 }

    if (applied >= steps.size) return

    db.beginTransaction()
    try {
      steps.drop(applied).forEachIndexed { index, step ->
        step(db)
        db.execSQL("INSERT INTO $VERSION_TABLE (version) VALUES (?)", arrayOf<Any>(applied + index + 1))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }
}
