package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DurationDownloadOption
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.domain.RetentionUnit
import org.grakovne.lissen.domain.RetentionWindow
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanAutoCacheTest {
  private fun file(
    id: String,
    size: Long,
    duration: Double,
  ) = BookFile(id = id, name = id, duration = duration, size = size, mimeType = "audio/mp4")

  private fun book(
    files: List<BookFile>,
    chapterDurations: List<Double>,
  ): DetailedItem {
    var start = 0.0
    return DetailedItem(
      id = "book",
      title = "Book",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = files,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      chapters =
        chapterDurations.mapIndexed { i, dur ->
          PlayingChapter(
            id = "c$i",
            title = "Chapter $i",
            start = start,
            end = (start + dur).also { start += dur },
            duration = dur,
            available = true,
            podcastEpisodeState = BookChapterState.FINISHED,
            index = i,
          )
        },
    )
  }

  // the reported bug: a 1.11 GB single-file book against a 1 GB ceiling cached nothing
  @Test
  fun `a window inside one huge file prefetches only that window, not the whole file`() {
    val book = book(listOf(file("f", size = 1_110_000_000, duration = 36_000.0)), List(10) { 3_600.0 })

    val plan =
      planAutoCache(
        AutoCacheInput(
          book = book,
          position = 0.0,
          lookahead = DurationDownloadOption(minutes = 120),
          ceilingBytes = 1_000_000_000,
        ),
      )

    // 2 h of a 10 h file is 20% = 222,000,000 bytes, plus the 5% margin = 233,100,000
    assertEquals(listOf(FileRange("f", ByteRange(0, 233_100_000))), plan.prefetch)
  }

  // 10 hours, one file, 10,000 bytes per second, ten one-hour chapters
  private val tenHours = book(listOf(file("f", size = 360_000_000, duration = 36_000.0)), List(10) { 3_600.0 })

  private fun fullPlan(
    book: DetailedItem = tenHours,
    position: Double = 0.0,
    lookahead: org.grakovne.lissen.domain.DownloadOption = DurationDownloadOption(120),
    ceilingBytes: Long = 1_000_000_000,
    cachedRanges: Map<String, List<ByteRange>> = emptyMap(),
    downloadedFileIds: Set<String> = emptySet(),
    retention: RetentionWindow = RetentionWindow(RetentionUnit.MINUTES, 5),
    rearmSeconds: Double = 0.0,
    otherBooks: List<CachedBookUsage> = emptyList(),
  ) = planAutoCache(
    AutoCacheInput(book, position, lookahead, ceilingBytes, cachedRanges, downloadedFileIds, retention, rearmSeconds, otherBooks),
  )

  private fun plan(
    book: DetailedItem = tenHours,
    position: Double = 0.0,
    lookahead: org.grakovne.lissen.domain.DownloadOption = DurationDownloadOption(120),
    ceilingBytes: Long = 1_000_000_000,
    cachedRanges: Map<String, List<ByteRange>> = emptyMap(),
    downloadedFileIds: Set<String> = emptySet(),
  ) = fullPlan(book, position, lookahead, ceilingBytes, cachedRanges, downloadedFileIds).prefetch

  private fun trim(
    position: Double,
    retention: RetentionWindow,
    cached: List<ByteRange>,
    rearmSeconds: Double = 0.0,
  ) = fullPlan(position = position, retention = retention, rearmSeconds = rearmSeconds, cachedRanges = mapOf("f" to cached)).trim

  @Test
  fun `the ceiling cuts the window to what fits instead of blocking it`() {
    // a 2 h window needs 75.6 MB with margin; only 50 MB is allowed
    assertEquals(listOf(FileRange("f", ByteRange(0, 50_000_000))), plan(ceilingBytes = 50_000_000))
  }

  @Test
  fun `bytes already cached are not fetched again and count against the ceiling`() {
    // 2 h = 72 MB + margin = 75.6 MB; the first 20 MB is cached, leaving 30 MB of the 50 MB ceiling
    val result = plan(ceilingBytes = 50_000_000, cachedRanges = mapOf("f" to listOf(ByteRange(0, 20_000_000))))

    assertEquals(listOf(FileRange("f", ByteRange(20_000_000, 50_000_000))), result)
  }

  @Test
  fun `a fully downloaded file is skipped`() {
    assertEquals(emptyList<FileRange>(), plan(downloadedFileIds = setOf("f")))
  }

  @Test
  fun `a window that crosses two files prefetches the tail of the first and the head of the second`() {
    val twoFiles =
      book(
        listOf(file("a", size = 600_000_000, duration = 18_000.0), file("b", size = 600_000_000, duration = 18_000.0)),
        List(10) { 3_600.0 },
      )

    // from 4.5 h (90% through "a"), 2 h ahead ends 30% through "b"; margin applies only to where the window ends
    assertEquals(
      listOf(
        FileRange("a", ByteRange(540_000_000, 600_000_000)),
        FileRange("b", ByteRange(0, 189_000_000)),
      ),
      plan(book = twoFiles, position = 16_200.0, ceilingBytes = 2_000_000_000),
    )
  }

  @Test
  fun `the current chapter option ends at the end of the current chapter`() {
    // chapter 1 is 3,600 - 7,200 s; from 4,000 s that is 40 MB to 72 MB, margin on the 32 MB window
    assertEquals(
      listOf(FileRange("f", ByteRange(40_000_000, 73_600_000))),
      plan(position = 4_000.0, lookahead = CurrentItemDownloadOption),
    )
  }

  @Test
  fun `a chapter count option ends at the end of the last of those chapters`() {
    // two chapters from chapter 1 end at 10,800 s = 108 MB; margin on the 68 MB window
    assertEquals(
      listOf(FileRange("f", ByteRange(40_000_000, 111_400_000))),
      plan(position = 4_000.0, lookahead = NumberItemDownloadOption(2)),
    )
  }

  @Test
  fun `remaining items runs to the end of the book`() {
    assertEquals(
      listOf(FileRange("f", ByteRange(40_000_000, 360_000_000))),
      plan(position = 4_000.0, lookahead = RemainingItemsDownloadOption),
    )
  }

  @Test
  fun `all items covers the whole book from the start when retention keeps everything behind`() {
    assertEquals(
      listOf(FileRange("f", ByteRange(0, 360_000_000))),
      fullPlan(position = 4_000.0, lookahead = AllItemsDownloadOption, retention = RetentionWindow(RetentionUnit.MINUTES, 1_000)).prefetch,
    )
  }

  @Test
  fun `minutes retention trims everything older than that many minutes behind the playhead`() {
    // at 7,200 s keeping 5 min means anything before 6,900 s = 69 MB goes
    assertEquals(
      listOf(FileRange("f", ByteRange(0, 69_000_000))),
      trim(7_200.0, RetentionWindow(RetentionUnit.MINUTES, 5), listOf(ByteRange(0, 100_000_000))),
    )
  }

  @Test
  fun `a cached range straddling the cutoff is trimmed only up to it`() {
    assertEquals(
      listOf(FileRange("f", ByteRange(50_000_000, 69_000_000))),
      trim(7_200.0, RetentionWindow(RetentionUnit.MINUTES, 5), listOf(ByteRange(50_000_000, 100_000_000))),
    )
  }

  @Test
  fun `cached audio at or after the cutoff is never trimmed`() {
    assertEquals(
      emptyList<FileRange>(),
      trim(7_200.0, RetentionWindow(RetentionUnit.MINUTES, 5), listOf(ByteRange(80_000_000, 100_000_000))),
    )
  }

  @Test
  fun `chapter retention keeps that many finished chapters behind the one playing`() {
    // at 19,000 s chapters 0-4 are finished and chapter 5 is playing; keeping 2 finished keeps chapters 3 and 4,
    // so everything before chapter 3 starts (10,800 s = 108 MB) goes
    assertEquals(
      listOf(FileRange("f", ByteRange(0, 108_000_000))),
      trim(19_000.0, RetentionWindow(RetentionUnit.CHAPTERS, 2), listOf(ByteRange(0, 200_000_000))),
    )
  }

  @Test
  fun `chapter retention trims nothing before any chapter has finished`() {
    assertEquals(
      emptyList<FileRange>(),
      trim(1_000.0, RetentionWindow(RetentionUnit.CHAPTERS, 2), listOf(ByteRange(0, 20_000_000))),
    )
  }

  @Test
  fun `sleep timer retention keeps a multiple of the re-arm extension`() {
    // 2 x 600 s = 1,200 s behind 7,200 s is 6,000 s = 60 MB
    assertEquals(
      listOf(FileRange("f", ByteRange(0, 60_000_000))),
      trim(
        7_200.0,
        RetentionWindow(RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE, 2),
        listOf(ByteRange(0, 100_000_000)),
        rearmSeconds = 600.0,
      ),
    )
  }

  @Test
  fun `space freed by trimming is available to the same plan's prefetch`() {
    // 100 MB cached of a 110 MB ceiling leaves 10 MB; trimming 69 MB leaves 79 MB for the 2 h window
    val result =
      fullPlan(
        position = 7_200.0,
        ceilingBytes = 110_000_000,
        cachedRanges = mapOf("f" to listOf(ByteRange(0, 100_000_000))),
      )

    assertEquals(listOf(FileRange("f", ByteRange(100_000_000, 147_600_000))), result.prefetch)
  }

  @Test
  fun `a whole-book window does not refetch what retention has let go of`() {
    // at 7,200 s keeping 5 min, the cutoff is 6,900 s = 69 MB, so the prefetch starts there and not at 0
    assertEquals(
      listOf(FileRange("f", ByteRange(69_000_000, 360_000_000))),
      plan(position = 7_200.0, lookahead = AllItemsDownloadOption),
    )
  }

  @Test
  fun `no book is evicted while the window fits`() {
    val result = fullPlan(ceilingBytes = 200_000_000, otherBooks = listOf(CachedBookUsage("a", 40_000_000, lastActiveAt = 1)))

    assertEquals(emptyList<String>(), result.evictBookIds)
  }

  @Test
  fun `the least recently played book is evicted first, and only as many as needed`() {
    // 2 h window = 75.6 MB; 80 MB used of 120 MB leaves 40 MB, so evicting the older 40 MB book is enough
    val result =
      fullPlan(
        ceilingBytes = 120_000_000,
        otherBooks = listOf(CachedBookUsage("newer", 40_000_000, lastActiveAt = 2), CachedBookUsage("older", 40_000_000, lastActiveAt = 1)),
      )

    assertEquals(listOf("older"), result.evictBookIds)
    assertEquals(listOf(FileRange("f", ByteRange(0, 75_600_000))), result.prefetch)
  }

  @Test
  fun `when evicting everything else still is not enough the window is cut to the ceiling`() {
    val result =
      fullPlan(ceilingBytes = 50_000_000, otherBooks = listOf(CachedBookUsage("a", 40_000_000, lastActiveAt = 1)))

    assertEquals(listOf("a"), result.evictBookIds)
    assertEquals(listOf(FileRange("f", ByteRange(0, 50_000_000))), result.prefetch)
  }

  @Test
  fun `nothing is evicted when there is nothing to prefetch`() {
    // the 2 h window is already cached, so even a ceiling already exceeded by other books evicts nothing
    val result =
      fullPlan(
        ceilingBytes = 10_000_000,
        cachedRanges = mapOf("f" to listOf(ByteRange(0, 80_000_000))),
        otherBooks = listOf(CachedBookUsage("a", 40_000_000, lastActiveAt = 1)),
      )

    assertEquals(emptyList<String>(), result.evictBookIds)
  }
}
