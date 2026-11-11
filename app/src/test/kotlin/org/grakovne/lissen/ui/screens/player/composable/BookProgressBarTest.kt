package org.grakovne.lissen.ui.screens.player.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookProgressBarTest {
  @Test
  fun `book progress is position over duration`() {
    assertEquals(0.25f, bookProgressFraction(position = 30.0, duration = 120.0))
  }

  @Test
  fun `book progress clamps past the end to full`() {
    assertEquals(1f, bookProgressFraction(position = 500.0, duration = 120.0))
  }

  @Test
  fun `book progress clamps a negative position to empty`() {
    assertEquals(0f, bookProgressFraction(position = -5.0, duration = 120.0))
  }

  @Test
  fun `book progress is empty for zero, negative or non-finite duration`() {
    listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
      assertEquals(0f, bookProgressFraction(position = 30.0, duration = it))
    }
  }

  @Test
  fun `book progress is empty for a non-finite position`() {
    assertEquals(0f, bookProgressFraction(position = Double.NaN, duration = 120.0))
  }
}
