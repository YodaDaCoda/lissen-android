package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.findRelatedFiles
import org.grakovne.lissen.content.cache.persistent.api.AutoCacheOwnershipRepository
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.NetworkType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.extensionSecondsFor
import timber.log.Timber
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class ContentAutoCachingService
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
    private val downloadPreferences: DownloadPreferences,
    private val playbackPreferences: PlaybackPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val networkService: NetworkService,
    private val contentCachingManager: ContentCachingManager,
    private val ownershipRepository: AutoCacheOwnershipRepository,
    private val contentCachingProgress: ContentCachingProgress,
  ) : RunningComponent {
    private var delayedJob: Job? = null
    private var lastBookId: String? = null

    /** Chapters a just-dispatched caching task will newly fetch, marked owned once it completes. */
    private val pendingOwnership = ConcurrentHashMap<String, List<String>>()

    private val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
          CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Auto-caching coroutine failed, ignoring")
          },
      )

    override fun onCreate() {
      scope.launch {
        contentCachingProgress.statusFlow.collect { (itemId, state) ->
          when (state.status) {
            CacheStatus.Completed -> {
              pendingOwnership.remove(itemId)?.let { chapterIds -> ownershipRepository.markOwned(itemId, chapterIds) }
            }

            CacheStatus.Error -> {
              pendingOwnership.remove(itemId)
            }

            else -> {}
          }
        }
      }

      scope.launch {
        combine(
          mediaRepository.playingBook,
          mediaRepository.isPlaying,
          mediaRepository.currentChapterIndex,
        ) { playingItem: DetailedItem?, isPlaying: Boolean, chapterIndex: Int ->
          Triple(playingItem, isPlaying, chapterIndex)
        }.distinctUntilChanged { old, new ->
          old.first?.id == new.first?.id && old.second == new.second && old.third == new.third
        }.collectLatest { (playingItem, isPlaying, _) ->
          if (playingItem?.id != lastBookId) {
            lastBookId?.let { oldBookId -> scope.launch { reclaim(oldBookId) } }
            lastBookId = playingItem?.id
          } else {
            playingItem?.let { trimConsumed(it) }
          }

          delayedJob?.cancel()
          delayedJob = updatePlaybackCache(playingItem, isPlaying)
        }
      }
    }

    /**
     * Releases a book's auto-cache-owned audio once it's no longer the active book. Whole-book
     * delete only when nothing manual is present (owned set == cached set) - otherwise only the
     * owned subset, via [ContentCachingManager.dropConsumedChapters], so a book that's part
     * auto-cache, part manual download never loses the manual part.
     */
    private suspend fun reclaim(bookId: String) {
      val owned = ownershipRepository.fetchOwnedChapterIds(bookId).toSet()
      if (owned.isEmpty()) return

      val cached = contentCachingManager.provideCachedChapterIds(bookId).first().toSet()

      if (isFullyAutoOwned(owned, cached)) {
        Timber.d("Reclaiming fully auto-owned cache for $bookId")
        contentCachingManager.dropCache(bookId)
      } else {
        val book = mediaProvider.fetchBook(bookId).fold(onSuccess = { it }, onFailure = { null }) ?: return
        val ownedChapters = book.chapters.filter { it.id in owned }
        val keptChapters = book.chapters.filterNot { it.id in owned }
        Timber.d("Reclaiming partially auto-owned cache for $bookId: ${ownedChapters.size} of ${book.chapters.size} chapters")
        contentCachingManager.dropConsumedChapters(book, droppingChapters = ownedChapters, keepingChapters = keptChapters)
      }

      ownershipRepository.clearAllOwned(bookId)
    }

    private suspend fun trimConsumed(book: DetailedItem) {
      val owned = ownershipRepository.fetchOwnedChapterIds(book.id).toSet()
      if (owned.isEmpty()) return

      val position = mediaRepository.totalPosition.value
      val retention = downloadPreferences.getAutoCacheRetentionWindow()
      val rearmSeconds =
        mediaRepository.timerOption.value
          ?.let { extensionSecondsFor(it, playbackPreferences.getSleepTimerSettings()) }
          ?: playbackPreferences.getSleepTimerSettings().rearmExtensionSeconds

      val toTrim = calculateChaptersToTrim(book.chapters, owned, position, retention, rearmSeconds.toDouble())
      if (toTrim.isEmpty()) return

      val trimIds = toTrim.map { it.id }.toSet()
      val keeping = book.chapters.filterNot { it.id in trimIds }

      contentCachingManager.dropConsumedChapters(book, droppingChapters = toTrim, keepingChapters = keeping)
      ownershipRepository.clearOwned(book.id, toTrim.map { it.id })
    }

    private suspend fun updatePlaybackCache(
      playingItem: DetailedItem?,
      isPlaying: Boolean,
      delayed: Boolean = false,
    ): Job? {
      val playbackCacheOption = downloadPreferences.getAutoDownloadOption() ?: return null
      val playingMediaItem = playingItem ?: return null

      val isNetworkAvailable = networkService.isNetworkAvailable()
      val currentNetwork = networkService.getCurrentNetworkType() ?: return null
      val preferredNetwork = downloadPreferences.getAutoDownloadNetworkType()
      val currentTotalPosition = mediaRepository.totalPosition.value

      val playingItemLibraryType =
        playingMediaItem.libraryType
          ?: mediaProvider
            .providePreferredChannel()
            .fetchLibraries()
            .fold(
              onSuccess = { libraries -> libraries.find { it.id == playingMediaItem.libraryId }?.type },
              onFailure = { null },
            ) ?: return null

      val requestedLibraryType =
        downloadPreferences
          .getAutoDownloadLibraryTypes()
          .contains(playingItemLibraryType)

      val isForceCache = libraryPreferences.isForceCache()

      val cacheAvailable =
        isNetworkAvailable &&
          isPlaying &&
          isForceCache.not() &&
          validNetworkType(currentNetwork, preferredNetwork) &&
          requestedLibraryType

      if (cacheAvailable.not()) return null

      if (downloadPreferences.getAutoDownloadDelayed().not() || delayed) {
        val requestedChapters = calculateRequestedChapters(playingMediaItem, playbackCacheOption, currentTotalPosition)
        val existingChapterIds = contentCachingManager.provideCachedChapterIds(playingMediaItem.id).first().toSet()
        val missingChapters = requestedChapters.filterNot { it.id in existingChapterIds }

        if (missingChapters.isEmpty()) return null

        if (exceedsStorageCeiling(playingMediaItem, missingChapters)) {
          Timber.d("Auto-cache for ${playingMediaItem.id} skipped this cycle: would exceed the storage ceiling")
          return null
        }

        pendingOwnership[playingMediaItem.id] = missingChapters.map { it.id }

        val task =
          ContentCachingTask(
            itemId = playingMediaItem.id,
            options = playbackCacheOption,
            currentPosition = currentTotalPosition,
            libraryType = playingItemLibraryType,
          )

        val intent =
          Intent(context, ContentCachingService::class.java).apply {
            action = ContentCachingService.CACHE_ITEM_ACTION
            putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
          }

        Timber.d("Auto-cache triggered for ${playingMediaItem.id}: option=$playbackCacheOption, position=${currentTotalPosition.toInt()}s")

        if (ContentCachingService.requestStart(context, intent).not()) {
          Timber.w("Caching service is unavailable, skipping auto-cache for ${playingMediaItem.id}")
          pendingOwnership.remove(playingMediaItem.id)
        }
        return null
      }

      Timber.d("Auto-cache delayed for ${playingMediaItem.id}: will retry in ${DELAY_TIME}ms")
      return scope.launch {
        val originalBookId = playingMediaItem.id
        delay(DELAY_TIME)

        val currentPlaying = mediaRepository.playingBook.value
        if (currentPlaying?.id != originalBookId) return@launch

        updatePlaybackCache(currentPlaying, isPlaying, delayed = true)
      }
    }

    /** A conservative, book-scoped estimate: this book's already-owned footprint plus what's newly requested. */
    private suspend fun exceedsStorageCeiling(
      book: DetailedItem,
      missingChapters: List<PlayingChapter>,
    ): Boolean {
      val ceiling = downloadPreferences.getAutoDownloadStorageCeilingBytes()
      val ownedIds = ownershipRepository.fetchOwnedChapterIds(book.id).toSet()
      val ownedChapters = book.chapters.filter { it.id in ownedIds }

      val ownedBytes = chaptersSizeBytes(book, ownedChapters)
      val addedBytes = chaptersSizeBytes(book, missingChapters)

      return ownedBytes + addedBytes > ceiling
    }

    private fun chaptersSizeBytes(
      book: DetailedItem,
      chapters: List<PlayingChapter>,
    ): Long =
      chapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }
        .sumOf { it.size ?: 0L }

    private fun validNetworkType(
      current: NetworkType,
      required: NetworkTypeAutoCache,
    ): Boolean {
      val positiveNetworkTypes =
        when (required) {
          NetworkTypeAutoCache.WIFI_ONLY -> listOf(NetworkType.WIFI)
          NetworkTypeAutoCache.WIFI_OR_CELLULAR -> listOf(NetworkType.WIFI, NetworkType.CELLULAR)
        }

      return positiveNetworkTypes.contains(current)
    }

    companion object {
      private const val DELAY_TIME: Long = 30_000
    }
  }

/** Whether every currently-cached chapter for a book is one auto-cache itself added. */
internal fun isFullyAutoOwned(
  owned: Set<String>,
  cached: Set<String>,
): Boolean = owned == cached
