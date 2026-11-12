package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LegacyAutoCacheCleanupTest {
  private fun chapter(
    id: String,
    available: Boolean,
  ) = PlayingChapter(
    id = id,
    title = id,
    start = 0.0,
    end = 10.0,
    duration = 10.0,
    available = available,
    podcastEpisodeState = BookChapterState.FINISHED,
  )

  @Test
  fun `the whole book goes when every downloaded chapter was auto-cached`() {
    val chapters = listOf(chapter("a", true), chapter("b", true), chapter("c", false))

    assertEquals(LegacyCleanup.DropBook, planLegacyCleanup(chapters, ownedIds = setOf("a", "b")))
  }

  @Test
  fun `only the auto-cached chapters go when the user downloaded others by hand`() {
    val a = chapter("a", true)
    val manual = chapter("m", true)
    val notCached = chapter("n", false)

    val plan = planLegacyCleanup(listOf(a, manual, notCached), ownedIds = setOf("a"))

    // a chapter that was never downloaded must not pin files, only the manual one is kept
    assertEquals(LegacyCleanup.DropChapters(dropping = listOf(a), keeping = listOf(manual)), plan)
  }

  @Test
  fun `chapters the book no longer has are ignored`() {
    assertNull(planLegacyCleanup(listOf(chapter("a", true)), ownedIds = setOf("gone")))
  }
}
