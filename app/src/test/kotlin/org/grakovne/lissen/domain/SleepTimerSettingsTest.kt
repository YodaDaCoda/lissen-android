package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepTimerSettingsTest {
  @Test
  fun `fade window is closed when fade is disabled`() {
    val settings = SleepTimerSettings(fadeEnabled = false, fadeSeconds = 30)
    assertFalse(settings.isWithinFadeWindow(10L))
  }

  @Test
  fun `fade window is open strictly inside the configured range`() {
    val settings = SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30)
    assertTrue(settings.isWithinFadeWindow(1L))
    assertTrue(settings.isWithinFadeWindow(30L))
    assertTrue(settings.isWithinFadeWindow(15L))
  }

  @Test
  fun `fade window excludes zero and anything past the configured seconds`() {
    val settings = SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30)
    assertFalse(settings.isWithinFadeWindow(0L))
    assertFalse(settings.isWithinFadeWindow(-5L))
    assertFalse(settings.isWithinFadeWindow(31L))
  }

  @Test
  fun `clamped keeps rearm extension, threshold, short and long rewind within their ranges`() {
    val tooHigh =
      SleepTimerSettings(
        rearmExtensionSeconds = 99_999,
        resumeRewindThresholdSeconds = 99_999,
        resumeRewindShortSeconds = 99_999,
        resumeRewindLongSeconds = 99_999,
      ).clamped()
    assertEquals(SleepTimerSettings.MAX_REARM_EXTENSION_SECONDS, tooHigh.rearmExtensionSeconds)
    assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_THRESHOLD_SECONDS, tooHigh.resumeRewindThresholdSeconds)
    assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_SHORT_SECONDS, tooHigh.resumeRewindShortSeconds)
    assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_LONG_SECONDS, tooHigh.resumeRewindLongSeconds)

    val tooLow =
      SleepTimerSettings(
        rearmExtensionSeconds = 0,
        resumeRewindThresholdSeconds = -5,
        resumeRewindShortSeconds = -5,
        resumeRewindLongSeconds = -5,
      ).clamped()
    assertEquals(SleepTimerSettings.MIN_REARM_EXTENSION_SECONDS, tooLow.rearmExtensionSeconds)
    assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_THRESHOLD_SECONDS, tooLow.resumeRewindThresholdSeconds)
    assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_SHORT_SECONDS, tooLow.resumeRewindShortSeconds)
    assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_LONG_SECONDS, tooLow.resumeRewindLongSeconds)
  }

  @Test
  fun `clamped leaves in-range values untouched`() {
    val settings =
      SleepTimerSettings(
        rearmExtensionSeconds = 120,
        resumeRewindThresholdSeconds = 90,
        resumeRewindShortSeconds = 45,
        resumeRewindLongSeconds = 600,
      )
    assertEquals(settings, settings.clamped())
  }

  @Test
  fun `extension and long-rewind modes default to fixed`() {
    val settings = SleepTimerSettings()
    assertEquals(RearmExtensionMode.FIXED, settings.rearmExtensionMode)
    assertEquals(ResumeRewindLongMode.FIXED, settings.resumeRewindLongMode)
  }

  @Test
  fun `both chimes default to enabled`() {
    val settings = SleepTimerSettings()
    assertTrue(settings.chimeOnFadeStart)
    assertTrue(settings.chimeOnRearm)
  }
}
