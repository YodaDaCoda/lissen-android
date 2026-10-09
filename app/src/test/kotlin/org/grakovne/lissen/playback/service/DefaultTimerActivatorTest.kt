package org.grakovne.lissen.playback.service

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DefaultTimerActivatorTest {
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private lateinit var activator: DefaultTimerActivator

  @BeforeEach
  fun setup() {
    activator = DefaultTimerActivator(preferences)
    every { preferences.getSleepTimerSettings() } returns SleepTimerSettings.Default
  }

  @Nested
  inner class WithoutSchedule {
    @Test
    fun `applies the default timer the first time playback starts`() {
      // DurationTimerOption has no equals() override, so the mock returns the same
      // instance each call and the assertion below compares against that same reference
      val option = DurationTimerOption(30)
      every { preferences.getDefaultTimerOption() } returns option

      var applied: TimerOption? = null
      activator.onPlaybackStarted { applied = it }

      assertEquals(option, applied)
    }

    @Test
    fun `does not apply again until a new book is prepared or the timer expires`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)

      var calls = 0
      activator.onPlaybackStarted { calls++ }
      activator.onPlaybackStarted { calls++ }

      assertEquals(1, calls)
    }

    @Test
    fun `a manual timer change suppresses the default for the rest of the session`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)

      activator.onTimerManuallySet()

      var calls = 0
      activator.onPlaybackStarted { calls++ }

      assertEquals(0, calls)
    }

    @Test
    fun `expiry re-arms the default for the next playback start`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)

      var calls = 0
      activator.onPlaybackStarted { calls++ }
      activator.onTimerExpired()
      activator.onPlaybackStarted { calls++ }

      assertEquals(2, calls)
    }

    @Test
    fun `a new book re-arms the default`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)

      var calls = 0
      activator.onPlaybackStarted { calls++ }
      activator.onNewBookPrepared()
      activator.onPlaybackStarted { calls++ }

      assertEquals(2, calls)
    }

    @Test
    fun `no default timer configured applies nothing`() {
      every { preferences.getDefaultTimerOption() } returns null

      var calls = 0
      activator.onPlaybackStarted { calls++ }

      assertEquals(0, calls)
    }
  }

  @Nested
  inner class WithSchedule {
    private val overnightSettings =
      SleepTimerSettings(
        defaultTimerScheduleEnabled = true,
        defaultTimerScheduleStartMinute = 20 * 60,
        defaultTimerScheduleEndMinute = 8 * 60,
      )

    @Test
    fun `applies the default timer when the current time is inside the overnight window`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)
      every { preferences.getSleepTimerSettings() } returns overnightSettings
      activator.nowMinuteOfDay = { 21 * 60 } // 9pm

      var applied = false
      activator.onPlaybackStarted { applied = true }

      assertTrue(applied)
    }

    @Test
    fun `does not apply the default timer when the current time is outside the window`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)
      every { preferences.getSleepTimerSettings() } returns overnightSettings
      activator.nowMinuteOfDay = { 14 * 60 } // 2pm

      var applied = false
      activator.onPlaybackStarted { applied = true }

      assertFalse(applied)
    }

    @Test
    fun `still consumes the pending flag even when outside the window`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)
      every { preferences.getSleepTimerSettings() } returns overnightSettings
      activator.nowMinuteOfDay = { 14 * 60 }

      var calls = 0
      activator.onPlaybackStarted { calls++ }

      // now it'd be in-window, but pending was already consumed by the first call
      activator.nowMinuteOfDay = { 21 * 60 }
      activator.onPlaybackStarted { calls++ }

      assertEquals(0, calls)
    }

    @Test
    fun `an unscheduled default timer ignores the time of day`() {
      every { preferences.getDefaultTimerOption() } returns DurationTimerOption(30)
      every { preferences.getSleepTimerSettings() } returns SleepTimerSettings.Default
      activator.nowMinuteOfDay = { 14 * 60 }

      var applied = false
      activator.onPlaybackStarted { applied = true }

      assertTrue(applied)
    }
  }

  @Nested
  inner class IsWithinMinuteWindowFn {
    @Test
    fun `a same-day window is inclusive of start and exclusive of end`() {
      assertTrue(isWithinMinuteWindow(9 * 60, 8 * 60, 20 * 60))
      assertTrue(isWithinMinuteWindow(8 * 60, 8 * 60, 20 * 60))
      assertFalse(isWithinMinuteWindow(20 * 60, 8 * 60, 20 * 60))
      assertFalse(isWithinMinuteWindow(7 * 60 + 59, 8 * 60, 20 * 60))
    }

    @Test
    fun `an overnight window wraps past midnight`() {
      assertTrue(isWithinMinuteWindow(21 * 60, 20 * 60, 8 * 60))
      assertTrue(isWithinMinuteWindow(0, 20 * 60, 8 * 60))
      assertTrue(isWithinMinuteWindow(7 * 60 + 59, 20 * 60, 8 * 60))
      assertFalse(isWithinMinuteWindow(8 * 60, 20 * 60, 8 * 60))
      assertFalse(isWithinMinuteWindow(14 * 60, 20 * 60, 8 * 60))
    }

    @Test
    fun `an empty window (start equals end) is never active`() {
      assertFalse(isWithinMinuteWindow(0, 600, 600))
      assertFalse(isWithinMinuteWindow(600, 600, 600))
      assertFalse(isWithinMinuteWindow(1200, 600, 600))
    }
  }
}
