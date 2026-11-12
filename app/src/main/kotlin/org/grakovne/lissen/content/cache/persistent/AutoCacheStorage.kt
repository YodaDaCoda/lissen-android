package org.grakovne.lissen.content.cache.persistent

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.playback.MediaModule
import org.grakovne.lissen.playback.service.LissenDataSourceFactory
import org.grakovne.lissen.playback.service.parseCacheKey
import org.grakovne.lissen.playback.service.toCacheKey
import org.grakovne.lissen.playback.service.toLissenUri
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The auto-cache's byte ranges, kept in a Media3 cache that playback already reads through. This is
 * the thin adapter around that cache; what to fetch and delete is decided by [planAutoCache].
 *
 * Media3 stores ranges as spans of at most a few MB, so trimming removes whole spans lying inside a
 * range and may leave a sliver of the last one.
 */
@Singleton
@OptIn(UnstableApi::class)
class AutoCacheStorage
  @Inject
  constructor(
    @param:Named(MediaModule.AUTO_CACHE) private val cache: Cache,
    private val dataSourceFactory: LissenDataSourceFactory,
  ) {
    fun cachedRanges(
      bookId: String,
      files: List<BookFile>,
    ): Map<String, List<ByteRange>> = files.associate { it.id to cachedRanges(toCacheKey(bookId, it.id)) }

    /** Bytes held per book. */
    fun bytesByBook(): Map<String, Long> =
      cache.keys
        .mapNotNull { key -> parseCacheKey(key)?.let { (bookId, _) -> bookId to cachedRanges(key).sumOf { it.length } } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }

    suspend fun prefetch(
      bookId: String,
      range: FileRange,
    ): Boolean =
      withContext(Dispatchers.IO) {
        val key = toCacheKey(bookId, range.fileId)
        val spec =
          DataSpec
            .Builder()
            .setUri(toLissenUri(bookId, range.fileId))
            .setKey(key)
            .setPosition(range.range.start)
            .setLength(range.range.length)
            .build()

        val writer = CacheWriter(dataSourceFactory.createPrefetchDataSource(), spec, null, null)

        // writer.cache() blocks, so a sibling coroutine stops it when this one is cancelled
        coroutineScope {
          val watcher = launch { try { awaitCancellation() } finally { writer.cancel() } }

          try {
            writer.cache()
            true
          } catch (ex: IOException) {
            Timber.w(ex, "Auto-cache prefetch failed for $key at ${range.range}")
            false
          } finally {
            watcher.cancel()
          }
        }
      }

    fun remove(
      bookId: String,
      range: FileRange,
    ) {
      val key = toCacheKey(bookId, range.fileId)
      cache
        .getCachedSpans(key)
        .filter { it.isCached && it.position >= range.range.start && it.position + it.length <= range.range.endExclusive }
        .forEach { cache.removeSpan(it) }
    }

    fun removeBook(bookId: String) =
      cache.keys
        .filter { parseCacheKey(it)?.first == bookId }
        .forEach { cache.removeResource(it) }

    private fun cachedRanges(key: String): List<ByteRange> =
      cache
        .getCachedSpans(key)
        .filter { it.isCached }
        .sortedBy { it.position }
        .fold(mutableListOf<ByteRange>()) { merged, span ->
          val next = ByteRange(span.position, span.position + span.length)
          val last = merged.lastOrNull()

          if (last != null && next.start <= last.endExclusive) {
            merged[merged.lastIndex] = ByteRange(last.start, maxOf(last.endExclusive, next.endExclusive))
          } else {
            merged += next
          }

          merged
        }
  }
