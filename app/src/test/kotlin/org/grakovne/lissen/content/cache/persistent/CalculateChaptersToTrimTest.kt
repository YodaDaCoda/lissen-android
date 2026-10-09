package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RetentionUnit
import org.grakovne.lissen.domain.RetentionWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CalculateChaptersToTrimTest {
  private fun chapters(vararg durations: Double): List<PlayingChapter> {
    var start = 0.0
    return durations.mapIndexed { i, dur ->
      PlayingChapter(
        id = "c$i",
        title = "Chapter $i",
        start = start,
        end = (start + dur).also { start += dur },
        duration = dur,
        available = true,
        podcastEpisodeState = BookChapterState.FINISHED,
      )
    }
  }

  @Nested
  inner class Minutes {
    @Test
    fun `a chapter ending exactly at the retention boundary is trimmed`() {
      val list = chapters(200.0, 200.0, 200.0, 200.0) // ends: 200, 400, 600, 800
      val owned = setOf("c0", "c1", "c2", "c3")
      val retention = RetentionWindow(RetentionUnit.MINUTES, 5) // 300s

      val result = calculateChaptersToTrim(list, owned, position = 700.0, retention = retention, rearmSeconds = 0.0)

      assertEquals(listOf("c0", "c1"), result.map { it.id })
    }

    @Test
    fun `a chapter just inside the retention window is not trimmed`() {
      val list = chapters(401.0) // ends at 401, one second past the 400s threshold
      val owned = setOf("c0")
      val retention = RetentionWindow(RetentionUnit.MINUTES, 5) // 300s, threshold at 700-300=400

      val result = calculateChaptersToTrim(list, owned, position = 700.0, retention = retention, rearmSeconds = 0.0)

      assertTrue(result.isEmpty(), "a chapter ending after the threshold must not be trimmed")
    }

    @Test
    fun `an already-played chapter that is not owned is never touched`() {
      val list = chapters(100.0, 100.0) // ends: 100, 200 - both well past any retention window
      val owned = setOf<String>() // neither chapter is auto-cache's to touch

      val result =
        calculateChaptersToTrim(list, owned, position = 500.0, retention = RetentionWindow(RetentionUnit.MINUTES, 1), rearmSeconds = 0.0)

      assertTrue(result.isEmpty(), "trim must never touch content it doesn't own")
    }
  }

  @Nested
  inner class SleepTimerRearmMultiple {
    @Test
    fun `resolves retention as the multiple of the given rearm seconds`() {
      val list = chapters(200.0, 200.0, 200.0, 200.0) // ends: 200, 400, 600, 800
      val owned = setOf("c0", "c1", "c2", "c3")
      val retention = RetentionWindow(RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE, 3)

      // 3 x 100s = 300s retained, same threshold (400) as the equivalent Minutes(5) case above
      val result = calculateChaptersToTrim(list, owned, position = 700.0, retention = retention, rearmSeconds = 100.0)

      assertEquals(listOf("c0", "c1"), result.map { it.id })
    }
  }

  @Nested
  inner class Chapters {
    @Test
    fun `keeps the configured count of most-recently-played owned chapters`() {
      val list = chapters(10.0, 10.0, 10.0, 10.0, 10.0) // ends: 10, 20, 30, 40, 50
      val owned = setOf("c0", "c1", "c2", "c3", "c4")
      val retention = RetentionWindow(RetentionUnit.CHAPTERS, 1)

      // played through c0, c1, c2 (position 35, partway into c3); keep the last 1 played chapter (c2)
      val result = calculateChaptersToTrim(list, owned, position = 35.0, retention = retention, rearmSeconds = 0.0)

      assertEquals(listOf("c0", "c1"), result.map { it.id })
    }

    @Test
    fun `does not trim a played chapter that is not owned`() {
      val list = chapters(10.0, 10.0, 10.0)
      val owned = setOf("c1") // c0 is manually downloaded, not auto-cache's to trim
      val retention = RetentionWindow(RetentionUnit.CHAPTERS, 0)

      val result = calculateChaptersToTrim(list, owned, position = 25.0, retention = retention, rearmSeconds = 0.0)

      assertEquals(listOf("c1"), result.map { it.id })
    }
  }
}
