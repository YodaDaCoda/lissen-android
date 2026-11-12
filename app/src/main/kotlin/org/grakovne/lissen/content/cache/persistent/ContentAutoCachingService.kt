package org.grakovne.lissen.content.cache.persistent

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.api.AutoCacheBookRepository
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.NetworkType
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.extensionSecondsFor
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the playing book's next stretch of audio in the auto-cache so playback rides out network
 * drops. What to fetch, trim and evict is decided by [planAutoCache]; this class gathers its input,
 * applies the plan, and decides when to run.
 */
@Singleton
@OptIn(UnstableApi::class)
class ContentAutoCachingService
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
    private val downloadPreferences: DownloadPreferences,
    private val playbackPreferences: PlaybackPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val networkService: NetworkService,
    private val localCacheRepository: LocalCacheRepository,
    private val storage: AutoCacheStorage,
    private val books: AutoCacheBookRepository,
  ) : RunningComponent {
    private var cachingJob: Job? = null

    private val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
          CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Auto-caching coroutine failed, ignoring")
          },
      )

    override fun onCreate() {
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
          if (playingItem != null && isPlaying) {
            books.touch(playingItem.id, playingItem.title, Instant.now().toEpochMilli())
          }

          cachingJob?.cancel()
          cachingJob = playingItem?.let { schedule(it, isPlaying) }
        }
      }
    }

    private fun schedule(
      book: DetailedItem,
      isPlaying: Boolean,
    ): Job =
      scope.launch {
        val option = eligibleOption(book, isPlaying) ?: return@launch

        if (downloadPreferences.getAutoDownloadDelayed()) {
          Timber.d("Auto-cache delayed for ${book.id}: will retry in ${DELAY_TIME}ms")
          delay(DELAY_TIME)

          if (mediaRepository.playingBook.value?.id != book.id) {
            Timber.d("Auto-cache skipped: book changed during the delay, was ${book.id}")
            return@launch
          }
        }

        cacheAhead(book, option)
      }

    /** The lookahead to use for [book], or null (with the reason logged) when auto-cache should not run now. */
    private suspend fun eligibleOption(
      book: DetailedItem,
      isPlaying: Boolean,
    ): DownloadOption? {
      val option = downloadPreferences.getAutoDownloadOption() ?: return skipped("no auto-download option is selected")

      val isNetworkAvailable = networkService.isNetworkAvailable()
      val currentNetwork = networkService.getCurrentNetworkType() ?: return skipped("current network type is unknown")
      val preferredNetwork = downloadPreferences.getAutoDownloadNetworkType()

      val libraryType =
        book.libraryType
          ?: mediaProvider
            .providePreferredChannel()
            .fetchLibraries()
            .fold(
              onSuccess = { libraries -> libraries.find { it.id == book.libraryId }?.type },
              onFailure = { null },
            ) ?: return skipped("library type of ${book.id} could not be resolved")

      val requestedLibraryType = downloadPreferences.getAutoDownloadLibraryTypes().contains(libraryType)
      val isForceCache = libraryPreferences.isForceCache()

      val eligible =
        isNetworkAvailable &&
          isPlaying &&
          isForceCache.not() &&
          validNetworkType(currentNetwork, preferredNetwork) &&
          requestedLibraryType

      if (eligible.not()) {
        return skipped(
          "${book.id} not eligible: networkAvailable=$isNetworkAvailable, playing=$isPlaying, " +
            "forceCache=$isForceCache, network=$currentNetwork (allowed: $preferredNetwork), " +
            "libraryType=$libraryType (selected: $requestedLibraryType)",
        )
      }

      return option
    }

    private suspend fun cacheAhead(
      book: DetailedItem,
      option: DownloadOption,
    ) {
      val settings = playbackPreferences.getSleepTimerSettings()
      val rearmSeconds =
        mediaRepository.timerOption.value
          ?.let { extensionSecondsFor(it, settings) }
          ?: settings.rearmExtensionSeconds

      val lastActive = books.fetchAll().associate { it.bookId to it.lastActiveAt }
      val others =
        storage
          .bytesByBook()
          .filterKeys { it != book.id }
          .map { (bookId, bytes) -> CachedBookUsage(bookId, bytes, lastActive[bookId] ?: 0L) }

      val plan =
        planAutoCache(
          AutoCacheInput(
            book = book,
            position = mediaRepository.totalPosition.value,
            lookahead = option,
            ceilingBytes = downloadPreferences.getAutoDownloadStorageCeilingBytes(),
            cachedRanges = storage.cachedRanges(book.id, book.files),
            downloadedFileIds = book.files.filter { localCacheRepository.provideFileUri(book.id, it.id) != null }.map { it.id }.toSet(),
            retention = downloadPreferences.getAutoCacheRetentionWindow(),
            rearmSeconds = rearmSeconds.toDouble(),
            otherBooks = others,
          ),
        )

      plan.evictBookIds.forEach {
        Timber.d("Auto-cache evicting $it to make room for ${book.id}")
        storage.removeBook(it)
        books.delete(it)
      }
      plan.trim.forEach { storage.remove(book.id, it) }

      if (plan.prefetch.isEmpty()) {
        skipped("nothing to prefetch for ${book.id}: the lookahead is cached, downloaded, or does not fit the ceiling")
        return
      }

      Timber.d("Auto-cache prefetching ${plan.prefetch.size} range(s) for ${book.id}: option=$option")
      plan.prefetch.forEach { range -> if (!storage.prefetch(book.id, range)) return }
    }

    private fun skipped(reason: String): DownloadOption? {
      Timber.d("Auto-cache skipped: $reason")
      return null
    }

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
