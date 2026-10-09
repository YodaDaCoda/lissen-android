package org.grakovne.lissen.content.cache.persistent.api

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.ForkSchema
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCacheOwnershipRepositoryTest {
  private lateinit var db: LocalCacheStorage
  private lateinit var repository: AutoCacheOwnershipRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room
        .inMemoryDatabaseBuilder(context, LocalCacheStorage::class.java)
        .addCallback(ForkSchema.callback)
        .allowMainThreadQueries()
        .build()
    repository = AutoCacheOwnershipRepository(db)

    db.openHelper.writableDatabase.execSQL(
      "INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt) VALUES ('book-1', 'Dune', 0, 0, 0)",
    )
  }

  @After
  fun teardown() = db.close()

  @Test
  fun marksFetchesAndClearsOwnedChapters() =
    runBlocking {
      repository.markOwned("book-1", listOf("ch-1", "ch-2", "ch-3"))
      assertEquals(listOf("ch-1", "ch-2", "ch-3"), repository.fetchOwnedChapterIds("book-1").sorted())
      assertEquals(listOf("book-1"), repository.fetchOwnedBookIds())

      repository.clearOwned("book-1", listOf("ch-2"))
      assertEquals(listOf("ch-1", "ch-3"), repository.fetchOwnedChapterIds("book-1").sorted())

      repository.clearAllOwned("book-1")
      assertEquals(emptyList<String>(), repository.fetchOwnedChapterIds("book-1"))
    }

  @Test
  fun ownershipDisappearsWithItsBook() =
    runBlocking {
      repository.markOwned("book-1", listOf("ch-1"))

      db.openHelper.writableDatabase.execSQL("DELETE FROM detailed_books WHERE id = 'book-1'")

      assertEquals(emptyList<String>(), repository.fetchOwnedBookIds())
    }

  @Test
  fun forkMigrationsRunOnceAndSurviveReopening() {
    val sqlite = db.openHelper.writableDatabase

    ForkSchema.migrate(sqlite)
    ForkSchema.migrate(sqlite)

    sqlite.query("SELECT COUNT(*) FROM fork_schema_version").use {
      it.moveToFirst()
      assertEquals(1, it.getInt(0))
    }
  }
}
