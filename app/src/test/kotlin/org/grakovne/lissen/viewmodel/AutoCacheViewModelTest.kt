package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.content.cache.persistent.AutoCachePause
import org.grakovne.lissen.content.cache.persistent.AutoCacheStorage
import org.grakovne.lissen.content.cache.persistent.api.AutoCacheBook
import org.grakovne.lissen.content.cache.persistent.api.AutoCacheBookRepository
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoCacheViewModelTest {
  private val storage = mockk<AutoCacheStorage>(relaxed = true)
  private val books = mockk<AutoCacheBookRepository>(relaxed = true)
  private val download = mockk<DownloadPreferences>(relaxed = true)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val pause = AutoCachePause()
  private val playingBook = MutableStateFlow<DetailedItem?>(null)

  private fun viewModel() = AutoCacheViewModel(storage, books, download, mediaRepository, pause)

  private fun playing(id: String) {
    playingBook.value = mockk<DetailedItem>(relaxed = true).also { every { it.id } returns id }
  }

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { mediaRepository.playingBook } returns playingBook
    every { download.getAutoDownloadStorageCeilingBytes() } returns 1_000_000_000L
    every { storage.bytesByBook() } returns mapOf("a" to 300_000_000L, "b" to 120_000_000L)
    coEvery { books.fetchAll() } returns
      listOf(AutoCacheBook("a", "Dune", lastActiveAt = 100), AutoCacheBook("b", "Emma", lastActiveAt = 200))
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `lists cached books with title and size, most recently played first, and sums the usage`() =
    runTest {
      val vm = viewModel()
      vm.refresh().join()

      assertEquals(listOf("Emma", "Dune"), vm.items.value.map { it.title })
      assertEquals(listOf(120_000_000L, 300_000_000L), vm.items.value.map { it.bytes })
      assertEquals(420_000_000L, vm.usedBytes.value)
      assertEquals(1_000_000_000L, vm.ceilingBytes)
    }

  @Test
  fun `clearing a book removes its audio and its record`() =
    runTest {
      val vm = viewModel()

      vm.clear("a").join()

      verify { storage.removeBook("a") }
      coVerify { books.delete("a") }
    }

  @Test
  fun `clearing the book that is playing pauses auto-cache for it so the delete sticks`() =
    runTest {
      playing("a")

      viewModel().clear("a").join()

      assertTrue(pause.isPaused("a"))
    }

  @Test
  fun `clearing a book that is not playing does not pause anything`() =
    runTest {
      playing("a")

      viewModel().clear("b").join()

      assertFalse(pause.isPaused("a"))
      assertFalse(pause.isPaused("b"))
    }

  @Test
  fun `clear all removes every book and pauses the one that is playing`() =
    runTest {
      playing("b")

      viewModel().clearAll().join()

      verify { storage.removeBook("a") }
      verify { storage.removeBook("b") }
      coVerify { books.deleteAll() }
      assertTrue(pause.isPaused("b"))
    }
}
