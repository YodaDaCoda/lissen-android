package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShakeRearmDetectorTest {
  @Test
  fun `stillness at rest is not a shake`() {
    // resting flat: one axis reads ~1g, the others ~0
    assertFalse(isShakeMagnitude(0f, 0f, 9.80665f))
  }

  @Test
  fun `a sharp jolt well above the threshold is a shake`() {
    assertTrue(isShakeMagnitude(30f, 30f, 30f))
  }

  @Test
  fun `exactly at the threshold is not yet a shake`() {
    val atThreshold = 9.80665f * 2.7f
    assertFalse(isShakeMagnitude(atThreshold, 0f, 0f, threshold = 2.7f))
  }

  @Test
  fun `just above the threshold is a shake`() {
    val justAbove = 9.80665f * 2.7f + 0.1f
    assertTrue(isShakeMagnitude(justAbove, 0f, 0f, threshold = 2.7f))
  }

  @Test
  fun `debounce blocks a second trigger inside the refractory window`() {
    assertFalse(debounceElapsed(now = 1_500L, lastTriggerMillis = 1_000L, minIntervalMillis = 1_000L))
  }

  @Test
  fun `debounce allows a trigger once the refractory window passed`() {
    assertTrue(debounceElapsed(now = 2_000L, lastTriggerMillis = 1_000L, minIntervalMillis = 1_000L))
  }

  @Test
  fun `debounce always allows the very first trigger`() {
    // lastTriggerMillis = 0L is the detector's initial state; a real epoch timestamp is
    // always far more than minIntervalMillis past zero
    assertTrue(debounceElapsed(now = 1_700_000_000_000L, lastTriggerMillis = 0L))
  }
}
