package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalculateFilesToDropTest {
  private fun chapter(
    id: String,
    start: Double,
    end: Double,
  ) = PlayingChapter(
    id = id,
    title = id,
    start = start,
    end = end,
    duration = end - start,
    available = true,
    podcastEpisodeState = BookChapterState.FINISHED,
  )

  private fun file(
    id: String,
    duration: Double,
  ) = BookFile(id = id, name = id, duration = duration, size = 0L, mimeType = "audio/mpeg")

  private fun book(
    files: List<BookFile>,
    chapters: List<PlayingChapter>,
  ) = DetailedItem(
    id = "book",
    title = "",
    subtitle = "",
    author = "",
    narrator = "",
    publisher = "",
    series = emptyList(),
    year = "",
    abstract = "",
    files = files,
    progress = null,
    libraryId = "lib",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
    chapters = chapters,
  )

  @Test
  fun `a file still needed by a kept chapter is not dropped`() {
    // one 30s file backing all three chapters - a common "one mp3, several chapters" layout
    val fileA = file("fileA", 30.0)
    val c0 = chapter("c0", 0.0, 10.0)
    val c1 = chapter("c1", 10.0, 20.0)
    val c2 = chapter("c2", 20.0, 30.0)
    val book = book(listOf(fileA), listOf(c0, c1, c2))

    val result = calculateFilesToDrop(book, droppingChapters = listOf(c0, c1), keepingChapters = listOf(c2))

    assertTrue(result.isEmpty(), "fileA still backs the kept c2 and must survive")
  }

  @Test
  fun `a file with no kept chapter is dropped`() {
    val fileA = file("fileA", 10.0)
    val fileB = file("fileB", 10.0)
    val c0 = chapter("c0", 0.0, 10.0)
    val c1 = chapter("c1", 10.0, 20.0)
    val book = book(listOf(fileA, fileB), listOf(c0, c1))

    val result = calculateFilesToDrop(book, droppingChapters = listOf(c0), keepingChapters = listOf(c1))

    assertEquals(listOf("fileA"), result.map { it.id })
  }
}
