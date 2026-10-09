package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.RearmExtensionMode
import org.grakovne.lissen.domain.ResumeRewindLongMode
import org.grakovne.lissen.domain.SleepTimerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackPreferencesSleepTimerSettingsTest {
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
  private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private lateinit var preferences: PlaybackPreferences

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    every { sharedPreferences.edit() } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns true
    preferences = PlaybackPreferences(SecurePreferenceStore(context), LibraryPreferences(SecurePreferenceStore(context)))
  }

  @Nested
  inner class GetSleepTimerSettings {
    @Test
    fun `returns Default when no preference stored`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns null
      assertEquals(SleepTimerSettings.Default, preferences.getSleepTimerSettings())
    }

    @Test
    fun `returns parsed value for current json format`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":45}"""
      assertEquals(SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45), preferences.getSleepTimerSettings())
    }

    @Test
    fun `applies kotlin defaults for absent fields`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeSeconds":20}"""
      assertEquals(SleepTimerSettings(fadeEnabled = false, fadeSeconds = 20), preferences.getSleepTimerSettings())
    }

    @Test
    fun `clamps fade seconds to the allowed range`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":999}"""
      assertEquals(SleepTimerSettings.MAX_FADE_SECONDS, preferences.getSleepTimerSettings().fadeSeconds)

      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":0}"""
      assertEquals(SleepTimerSettings.MIN_FADE_SECONDS, preferences.getSleepTimerSettings().fadeSeconds)
    }

    @Test
    fun `clamps rearm extension, threshold, short and long rewind seconds to their allowed ranges`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"rearmExtensionSeconds":99999,"resumeRewindThresholdSeconds":99999,""" +
        """"resumeRewindShortSeconds":99999,"resumeRewindLongSeconds":99999}"""
      val tooHigh = preferences.getSleepTimerSettings()
      assertEquals(SleepTimerSettings.MAX_REARM_EXTENSION_SECONDS, tooHigh.rearmExtensionSeconds)
      assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_THRESHOLD_SECONDS, tooHigh.resumeRewindThresholdSeconds)
      assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_SHORT_SECONDS, tooHigh.resumeRewindShortSeconds)
      assertEquals(SleepTimerSettings.MAX_RESUME_REWIND_LONG_SECONDS, tooHigh.resumeRewindLongSeconds)

      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"rearmExtensionSeconds":0,"resumeRewindThresholdSeconds":-5,""" +
        """"resumeRewindShortSeconds":-5,"resumeRewindLongSeconds":-5}"""
      val tooLow = preferences.getSleepTimerSettings()
      assertEquals(SleepTimerSettings.MIN_REARM_EXTENSION_SECONDS, tooLow.rearmExtensionSeconds)
      assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_THRESHOLD_SECONDS, tooLow.resumeRewindThresholdSeconds)
      assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_SHORT_SECONDS, tooLow.resumeRewindShortSeconds)
      assertEquals(SleepTimerSettings.MIN_RESUME_REWIND_LONG_SECONDS, tooLow.resumeRewindLongSeconds)
    }

    @Test
    fun `applies kotlin defaults for absent re-arm, chime and resume-rewind fields`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":20}"""
      val settings = preferences.getSleepTimerSettings()

      assertEquals(true, settings.chimeOnFadeStart)
      assertEquals(false, settings.rearmEnabled)
      assertEquals(true, settings.chimeOnRearm)
      assertEquals(true, settings.rearmViaHeadphoneButton)
      assertEquals(true, settings.rearmViaShake)
      assertEquals(RearmExtensionMode.FIXED, settings.rearmExtensionMode)
      assertEquals(SleepTimerSettings.DEFAULT_REARM_EXTENSION_SECONDS, settings.rearmExtensionSeconds)
      assertEquals(SleepTimerSettings.DEFAULT_RESUME_REWIND_THRESHOLD_SECONDS, settings.resumeRewindThresholdSeconds)
      assertEquals(SleepTimerSettings.DEFAULT_RESUME_REWIND_SHORT_SECONDS, settings.resumeRewindShortSeconds)
      assertEquals(ResumeRewindLongMode.FIXED, settings.resumeRewindLongMode)
      assertEquals(SleepTimerSettings.DEFAULT_RESUME_REWIND_LONG_SECONDS, settings.resumeRewindLongSeconds)
    }

    @Test
    fun `clamps chime volumes and schedule minutes to their allowed ranges`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"chimeFadeVolume":999,"chimeRearmVolume":999,""" +
        """"defaultTimerScheduleStartMinute":9999,"defaultTimerScheduleEndMinute":9999}"""
      val tooHigh = preferences.getSleepTimerSettings()
      assertEquals(SleepTimerSettings.MAX_CHIME_VOLUME, tooHigh.chimeFadeVolume)
      assertEquals(SleepTimerSettings.MAX_CHIME_VOLUME, tooHigh.chimeRearmVolume)
      assertEquals(SleepTimerSettings.MAX_SCHEDULE_MINUTE, tooHigh.defaultTimerScheduleStartMinute)
      assertEquals(SleepTimerSettings.MAX_SCHEDULE_MINUTE, tooHigh.defaultTimerScheduleEndMinute)

      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"chimeFadeVolume":-5,"chimeRearmVolume":-5,""" +
        """"defaultTimerScheduleStartMinute":-5,"defaultTimerScheduleEndMinute":-5}"""
      val tooLow = preferences.getSleepTimerSettings()
      assertEquals(SleepTimerSettings.MIN_CHIME_VOLUME, tooLow.chimeFadeVolume)
      assertEquals(SleepTimerSettings.MIN_CHIME_VOLUME, tooLow.chimeRearmVolume)
      assertEquals(SleepTimerSettings.MIN_SCHEDULE_MINUTE, tooLow.defaultTimerScheduleStartMinute)
      assertEquals(SleepTimerSettings.MIN_SCHEDULE_MINUTE, tooLow.defaultTimerScheduleEndMinute)
    }

    @Test
    fun `applies kotlin defaults for absent chime volume and schedule fields`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":20}"""
      val settings = preferences.getSleepTimerSettings()

      assertEquals(SleepTimerSettings.DEFAULT_CHIME_VOLUME, settings.chimeFadeVolume)
      assertEquals(SleepTimerSettings.DEFAULT_CHIME_VOLUME, settings.chimeRearmVolume)
      assertEquals(false, settings.defaultTimerScheduleEnabled)
      assertEquals(SleepTimerSettings.DEFAULT_SCHEDULE_START_MINUTE, settings.defaultTimerScheduleStartMinute)
      assertEquals(SleepTimerSettings.DEFAULT_SCHEDULE_END_MINUTE, settings.defaultTimerScheduleEndMinute)
    }

    @Test
    fun `returns Default and clears preference for malformed json`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeSeconds":"many"}"""

      assertEquals(SleepTimerSettings.Default, preferences.getSleepTimerSettings())
      verify { editor.remove("sleep_timer_settings") }
      verify { editor.commit() }
    }
  }

  @Nested
  inner class SaveSleepTimerSettings {
    @Test
    fun `writes json and commits`() {
      preferences.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45))

      verify {
        editor.putString(
          "sleep_timer_settings",
          """{"fadeEnabled":true,"fadeSeconds":45,"chimeOnFadeStart":true,"rearmEnabled":false,"chimeOnRearm":true,""" +
            """"rearmViaHeadphoneButton":true,"rearmViaShake":true,"rearmExtensionMode":"FIXED",""" +
            """"rearmExtensionSeconds":300,"resumeRewindThresholdSeconds":120,"resumeRewindShortSeconds":0,""" +
            """"resumeRewindLongMode":"FIXED","resumeRewindLongSeconds":0,"chimeFadeVolume":50,"chimeRearmVolume":50,"defaultTimerScheduleEnabled":false,"defaultTimerScheduleStartMinute":1200,"defaultTimerScheduleEndMinute":480}""",
        )
      }
      verify { editor.commit() }
    }

    @Test
    fun `clamps fade seconds before writing`() {
      preferences.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = false, fadeSeconds = 999))

      verify {
        editor.putString(
          "sleep_timer_settings",
          """{"fadeEnabled":false,"fadeSeconds":60,"chimeOnFadeStart":true,"rearmEnabled":false,"chimeOnRearm":true,""" +
            """"rearmViaHeadphoneButton":true,"rearmViaShake":true,"rearmExtensionMode":"FIXED",""" +
            """"rearmExtensionSeconds":300,"resumeRewindThresholdSeconds":120,"resumeRewindShortSeconds":0,""" +
            """"resumeRewindLongMode":"FIXED","resumeRewindLongSeconds":0,"chimeFadeVolume":50,"chimeRearmVolume":50,"defaultTimerScheduleEnabled":false,"defaultTimerScheduleStartMinute":1200,"defaultTimerScheduleEndMinute":480}""",
        )
      }
    }

    @Test
    fun `clamps rearm extension, threshold, short and long rewind seconds before writing`() {
      preferences.saveSleepTimerSettings(
        SleepTimerSettings(
          rearmExtensionSeconds = 1,
          resumeRewindThresholdSeconds = -5,
          resumeRewindShortSeconds = -5,
          resumeRewindLongSeconds = -5,
        ),
      )

      verify {
        editor.putString(
          "sleep_timer_settings",
          """{"fadeEnabled":false,"fadeSeconds":30,"chimeOnFadeStart":true,"rearmEnabled":false,"chimeOnRearm":true,""" +
            """"rearmViaHeadphoneButton":true,"rearmViaShake":true,"rearmExtensionMode":"FIXED",""" +
            """"rearmExtensionSeconds":30,"resumeRewindThresholdSeconds":0,"resumeRewindShortSeconds":0,""" +
            """"resumeRewindLongMode":"FIXED","resumeRewindLongSeconds":0,"chimeFadeVolume":50,"chimeRearmVolume":50,"defaultTimerScheduleEnabled":false,"defaultTimerScheduleStartMinute":1200,"defaultTimerScheduleEndMinute":480}""",
        )
      }
    }
  }
}
