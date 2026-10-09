package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RetentionUnit
import org.grakovne.lissen.domain.RetentionWindow

/**
 * Owned chapters now strictly behind the playhead, minus [retention]'s buffer - never infers
 * anything, only ever looks backward at what's monotonically already been played.
 *
 * [rearmSeconds] is only consulted for [RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE] (the caller
 * resolves it, e.g. via `extensionSecondsFor`, so this function stays pure and needs no sleep
 * timer dependency of its own).
 */
fun calculateChaptersToTrim(
  chapters: List<PlayingChapter>,
  ownedChapterIds: Set<String>,
  position: Double,
  retention: RetentionWindow,
  rearmSeconds: Double,
): List<PlayingChapter> {
  val owned = chapters.filter { it.id in ownedChapterIds }

  return when (retention.unit) {
    RetentionUnit.CHAPTERS -> {
      val lastPlayedIndex = chapters.indexOfLast { it.end <= position }
      if (lastPlayedIndex < 0) return emptyList()
      val cutoffIndex = lastPlayedIndex - retention.amount

      chapters
        .withIndex()
        .filter { (index, chapter) -> index <= cutoffIndex && chapter.id in ownedChapterIds && chapter.end <= position }
        .map { it.value }
    }

    RetentionUnit.MINUTES -> {
      val retainSeconds = retention.amount * 60.0
      owned.filter { it.end <= position - retainSeconds }
    }

    RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> {
      val retainSeconds = retention.amount * rearmSeconds
      owned.filter { it.end <= position - retainSeconds }
    }
  }
}
