package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds auto-cache off for a book the user just cleared, so the clear is not undone by an immediate
 * refill. In memory only: it ends at the next play start, or when the app restarts.
 */
@Singleton
class AutoCachePause
  @Inject
  constructor() {
    private val _paused = MutableStateFlow<String?>(null)
    val paused: StateFlow<String?> = _paused.asStateFlow()

    fun pause(bookId: String) {
      _paused.value = bookId
    }

    fun resume() {
      _paused.value = null
    }

    fun isPaused(bookId: String): Boolean = _paused.value == bookId
  }
