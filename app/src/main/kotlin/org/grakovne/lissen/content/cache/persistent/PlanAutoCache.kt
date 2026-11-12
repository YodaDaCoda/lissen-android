package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.DurationDownloadOption
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.domain.RetentionUnit
import org.grakovne.lissen.domain.RetentionWindow
import org.grakovne.lissen.playback.service.calculateChapterIndex
import kotlin.math.floor
import kotlin.math.roundToLong

/** A half-open byte range within one audio file. */
data class ByteRange(
  val start: Long,
  val endExclusive: Long,
) {
  val length: Long get() = endExclusive - start
}

data class FileRange(
  val fileId: String,
  val range: ByteRange,
)

/** What another auto-cached book occupies, and when it was last played on this device. */
data class CachedBookUsage(
  val bookId: String,
  val bytes: Long,
  val lastActiveAt: Long,
)

data class AutoCacheInput(
  val book: DetailedItem,
  val position: Double,
  val lookahead: DownloadOption,
  val ceilingBytes: Long,
  /** Ranges of this book already in the auto-cache, by file id. */
  val cachedRanges: Map<String, List<ByteRange>> = emptyMap(),
  /** Files saved whole by the user; playback reads them locally, so auto-cache never stores them again. */
  val downloadedFileIds: Set<String> = emptySet(),
  val retention: RetentionWindow = RetentionWindow.DEFAULT,
  /** Only consulted for [RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE]; the caller resolves it. */
  val rearmSeconds: Double = 0.0,
  /** Every other auto-cached book; the playing book is never among them, so it is never evicted. */
  val otherBooks: List<CachedBookUsage> = emptyList(),
)

data class AutoCachePlan(
  val prefetch: List<FileRange>,
  /** Already-played audio behind the retention window, to remove from the cache. */
  val trim: List<FileRange> = emptyList(),
  /** Whole books to delete, least recently played first, so the playing book's window fits. */
  val evictBookIds: List<String> = emptyList(),
)

/**
 * What auto-cache should do next for the playing book. Pure: no I/O, no Android, so the rules
 * (lookahead, retention, ceiling, margin) are testable without a device.
 *
 * Time is mapped to bytes as `size * (time / duration)`; the estimate is approximate (VBR audio,
 * container overhead), so the end of the window gets [MARGIN] extra and streaming fills any shortfall.
 */
fun planAutoCache(input: AutoCacheInput): AutoCachePlan {
  val cutoff = retentionCutoff(input)
  val trim = rangesBefore(input, cutoff)
  val cachedAfterTrim = input.cachedRanges.mapValues { (fileId, ranges) -> ranges.subtract(trim.filter { it.fileId == fileId }.map { it.range }) }

  val (from, to) = lookaheadWindow(input) ?: return AutoCachePlan(prefetch = emptyList(), trim = trim)
  // never re-fetch audio the retention window has just let go of
  val window = maxOf(from, cutoff) to to

  var fileStart = 0.0
  val wanted =
    input.book.files.mapNotNull { file ->
      val size = file.size
      val range =
        if (size == null || file.duration <= 0.0 || file.id in input.downloadedFileIds) {
          null
        } else {
          rangeInFile(file.id, size, file.duration, fileStart, window.first, window.second)
        }
      fileStart += file.duration
      range
    }

  val missing = wanted.flatMap { w -> listOf(w.range).subtract(cachedAfterTrim[w.fileId].orEmpty()).map { FileRange(w.fileId, it) } }
  val used = cachedAfterTrim.values.sumOf { ranges -> ranges.sumOf { it.length } } + input.otherBooks.sumOf { it.bytes }

  val needed = missing.sumOf { it.range.length }
  val evicted = if (needed > 0) booksToEvict(input.otherBooks, needed, input.ceilingBytes - used) else emptyList()
  val budget = input.ceilingBytes - used + evicted.sumOf { it.bytes }

  return AutoCachePlan(
    prefetch = missing.withinBudget(budget.coerceAtLeast(0)),
    trim = trim,
    evictBookIds = evicted.map { it.bookId },
  )
}

/** Oldest-played books first, as few as free up [needed] bytes; all of them if that still is not enough. */
private fun booksToEvict(
  others: List<CachedBookUsage>,
  needed: Long,
  free: Long,
): List<CachedBookUsage> {
  var room = free
  return others
    .sortedBy { it.lastActiveAt }
    .takeWhile { book ->
      (room < needed).also { if (it) room += book.bytes }
    }
}

/** The book time before which already-played audio is let go. Never later than the playhead. */
private fun retentionCutoff(input: AutoCacheInput): Double {
  val retention = input.retention
  val cutoff =
    when (retention.unit) {
      RetentionUnit.MINUTES -> input.position - retention.amount * 60.0

      RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> input.position - retention.amount * input.rearmSeconds

      RetentionUnit.CHAPTERS -> {
        val chapters = input.book.chapters
        val lastFinished = chapters.indexOfLast { it.end <= input.position }
        if (lastFinished < 0) 0.0 else chapters[(lastFinished - retention.amount + 1).coerceAtLeast(0)].start
      }
    }

  return cutoff.coerceIn(0.0, input.position)
}

/** Cached ranges lying before [cutoff] of book time. */
private fun rangesBefore(
  input: AutoCacheInput,
  cutoff: Double,
): List<FileRange> {
  var fileStart = 0.0
  return input.book.files.flatMap { file ->
    val size = file.size
    val start = fileStart
    fileStart += file.duration

    if (size == null || file.duration <= 0.0) return@flatMap emptyList()

    val cutoffByte = floor(size * (cutoff - start) / file.duration).toLong().coerceIn(0, size)
    input.cachedRanges[file.id].orEmpty()
      .filter { it.start < cutoffByte }
      .map { FileRange(file.id, ByteRange(it.start, minOf(it.endExclusive, cutoffByte))) }
  }
}

/** The parts of these ranges not covered by any of [holes]. */
private fun List<ByteRange>.subtract(holes: List<ByteRange>): List<ByteRange> {
  var gaps = this
  holes.forEach { hole ->
    gaps =
      gaps.flatMap { gap ->
        if (hole.endExclusive <= gap.start || gap.endExclusive <= hole.start) {
          listOf(gap)
        } else {
          listOfNotNull(
            ByteRange(gap.start, hole.start).takeIf { it.length > 0 },
            ByteRange(hole.endExclusive, gap.endExclusive).takeIf { it.length > 0 },
          )
        }
      }
  }
  return gaps
}

/** Keeps ranges in playback order until [budget] bytes are spent, cutting the last one short. */
private fun List<FileRange>.withinBudget(budget: Long): List<FileRange> {
  var left = budget
  return mapNotNull { file ->
    val take = minOf(file.range.length, left)
    left -= take
    file.takeIf { take > 0 }?.copy(range = ByteRange(file.range.start, file.range.start + take))
  }
}

/** The span of book time to have cached, from the playhead. Null when the option asks for nothing. */
private fun lookaheadWindow(input: AutoCacheInput): Pair<Double, Double>? =
  when (val option = input.lookahead) {
    is DurationDownloadOption -> input.position to input.position + option.minutes * 60.0
    CurrentItemDownloadOption -> input.position to endOfChapters(input, 1)
    is NumberItemDownloadOption -> input.position to endOfChapters(input, option.itemsNumber)
    RemainingItemsDownloadOption -> input.position to Double.MAX_VALUE
    AllItemsDownloadOption -> 0.0 to Double.MAX_VALUE
  }

/** Where the [count]th chapter, counting from the one at the playhead, ends. */
private fun endOfChapters(
  input: AutoCacheInput,
  count: Int,
): Double {
  val chapters = input.book.chapters
  val first = calculateChapterIndex(input.book, input.position).coerceAtLeast(0)
  val last = (first + count - 1).coerceIn(0, chapters.lastIndex)
  return chapters.getOrNull(last)?.end ?: input.position
}

private fun rangeInFile(
  fileId: String,
  size: Long,
  duration: Double,
  fileStart: Double,
  from: Double,
  to: Double,
): FileRange? {
  val fileEnd = fileStart + duration
  val windowFrom = maxOf(from, fileStart)
  val windowTo = minOf(to, fileEnd)
  if (windowTo <= windowFrom) return null

  fun bytesAt(time: Double) = size * (time - fileStart) / duration

  val start = bytesAt(windowFrom)
  val windowBytes = bytesAt(windowTo) - start
  // the margin only matters where the window ends inside the file; otherwise it ends at the file's end anyway
  val end = if (to < fileEnd) start + windowBytes * (1 + MARGIN) else size.toDouble()

  return FileRange(fileId, ByteRange(start.roundToLong(), minOf(end.roundToLong(), size)))
}

private const val MARGIN = 0.05
