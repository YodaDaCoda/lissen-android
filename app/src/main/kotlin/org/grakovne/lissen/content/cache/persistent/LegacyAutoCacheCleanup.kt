package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.LegacyAutoCacheOwnershipRepository
import org.grakovne.lissen.domain.PlayingChapter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LegacyCleanup {
  data object DropBook : LegacyCleanup

  data class DropChapters(
    val dropping: List<PlayingChapter>,
    val keeping: List<PlayingChapter>,
  ) : LegacyCleanup
}

/**
 * What to remove of the whole-file audio the old auto-cache stored. Only chapters recorded as
 * auto-owned are ever dropped, so a manual download is never listed. Chapters that were never
 * downloaded are not kept, because they would pin files they merely overlap.
 */
fun planLegacyCleanup(
  chapters: List<PlayingChapter>,
  ownedIds: Set<String>,
): LegacyCleanup? {
  val dropping = chapters.filter { it.id in ownedIds }
  if (dropping.isEmpty()) return null

  val keeping = chapters.filter { it.available && it.id !in ownedIds }
  return if (keeping.isEmpty()) LegacyCleanup.DropBook else LegacyCleanup.DropChapters(dropping, keeping)
}

/** Runs once at launch: deletes what the old auto-cache stored as whole files, then drops its table. */
@Singleton
class LegacyAutoCacheCleanup
  @Inject
  constructor(
    private val ownership: LegacyAutoCacheOwnershipRepository,
    private val bookRepository: CachedBookRepository,
    private val contentCachingManager: ContentCachingManager,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
      scope.launch {
        try {
          cleanUp()
        } catch (ex: Exception) {
          Timber.e(ex, "Legacy auto-cache cleanup failed, will retry on the next launch")
        }
      }
    }

    private suspend fun cleanUp() {
      ownership.fetchOwned().forEach { (bookId, ownedIds) ->
        val book = bookRepository.fetchBook(bookId) ?: return@forEach

        when (val plan = planLegacyCleanup(book.chapters, ownedIds)) {
          LegacyCleanup.DropBook -> contentCachingManager.dropCache(bookId)
          is LegacyCleanup.DropChapters -> contentCachingManager.dropConsumedChapters(book, plan.dropping, plan.keeping)
          null -> {}
        }
      }

      ownership.dropTable()
    }
  }
