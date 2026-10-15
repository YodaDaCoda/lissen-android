package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.AutoCachePause
import org.grakovne.lissen.content.cache.persistent.AutoCacheStorage
import org.grakovne.lissen.content.cache.persistent.api.AutoCacheBookRepository
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import javax.inject.Inject

data class AutoCacheBookItem(
  val bookId: String,
  val title: String,
  val bytes: Long,
  val lastActiveAt: Long,
)

@HiltViewModel
class AutoCacheViewModel
  @Inject
  constructor(
    private val storage: AutoCacheStorage,
    private val books: AutoCacheBookRepository,
    private val download: DownloadPreferences,
    private val mediaRepository: MediaRepository,
    private val pause: AutoCachePause,
  ) : ViewModel() {
    private val _items = MutableStateFlow<List<AutoCacheBookItem>>(emptyList())
    val items: StateFlow<List<AutoCacheBookItem>> = _items.asStateFlow()

    private val _usedBytes = MutableStateFlow(0L)
    val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()

    val ceilingBytes: Long get() = download.getAutoDownloadStorageCeilingBytes()

    fun refresh(): Job =
      viewModelScope.launch {
        val (bytes, titles) = withContext(Dispatchers.IO) { storage.bytesByBook() to books.fetchAll().associateBy { it.bookId } }

        _items.value =
          bytes
            .filterValues { it > 0 }
            .map { (bookId, size) ->
              AutoCacheBookItem(bookId, titles[bookId]?.title ?: bookId, size, titles[bookId]?.lastActiveAt ?: 0L)
            }.sortedByDescending { it.lastActiveAt }
        _usedBytes.value = bytes.values.sum()
      }

    fun clear(bookId: String): Job {
      Timber.d("User action: clear auto-cache for $bookId")

      return viewModelScope.launch {
        pauseIfPlaying(bookId)
        withContext(Dispatchers.IO) {
          storage.removeBook(bookId)
          books.delete(bookId)
        }
        refresh().join()
      }
    }

    fun clearAll(): Job {
      Timber.d("User action: clear all auto-cache")

      return viewModelScope.launch {
        val bookIds = withContext(Dispatchers.IO) { storage.bytesByBook().keys }
        bookIds.forEach { pauseIfPlaying(it) }

        withContext(Dispatchers.IO) {
          bookIds.forEach { storage.removeBook(it) }
          books.deleteAll()
        }
        refresh().join()
      }
    }

    private fun pauseIfPlaying(bookId: String) {
      if (mediaRepository.playingBook.value?.id == bookId) pause.pause(bookId)
    }
  }
