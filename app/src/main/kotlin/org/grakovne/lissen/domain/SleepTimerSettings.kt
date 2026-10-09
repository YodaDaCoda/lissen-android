package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SleepTimerSettings(
  val fadeEnabled: Boolean = false,
  val fadeSeconds: Int = DEFAULT_FADE_SECONDS,
  val chimeOnFadeStart: Boolean = true,
  val rearmEnabled: Boolean = false,
  val chimeOnRearm: Boolean = true,
  val rearmViaHeadphoneButton: Boolean = true,
  val rearmViaShake: Boolean = true,
  val rearmExtensionMode: RearmExtensionMode = RearmExtensionMode.FIXED,
  val rearmExtensionSeconds: Int = DEFAULT_REARM_EXTENSION_SECONDS,
  val resumeRewindThresholdSeconds: Int = DEFAULT_RESUME_REWIND_THRESHOLD_SECONDS,
  val resumeRewindShortSeconds: Int = DEFAULT_RESUME_REWIND_SHORT_SECONDS,
  val resumeRewindLongMode: ResumeRewindLongMode = ResumeRewindLongMode.FIXED,
  val resumeRewindLongSeconds: Int = DEFAULT_RESUME_REWIND_LONG_SECONDS,
  val chimeFadeVolume: Int = DEFAULT_CHIME_VOLUME,
  val chimeRearmVolume: Int = DEFAULT_CHIME_VOLUME,
  val defaultTimerScheduleEnabled: Boolean = false,
  val defaultTimerScheduleStartMinute: Int = DEFAULT_SCHEDULE_START_MINUTE,
  val defaultTimerScheduleEndMinute: Int = DEFAULT_SCHEDULE_END_MINUTE,
) {
  fun clamped(): SleepTimerSettings =
    copy(
      fadeSeconds = fadeSeconds.coerceIn(MIN_FADE_SECONDS, MAX_FADE_SECONDS),
      rearmExtensionSeconds = rearmExtensionSeconds.coerceIn(MIN_REARM_EXTENSION_SECONDS, MAX_REARM_EXTENSION_SECONDS),
      resumeRewindThresholdSeconds =
        resumeRewindThresholdSeconds.coerceIn(MIN_RESUME_REWIND_THRESHOLD_SECONDS, MAX_RESUME_REWIND_THRESHOLD_SECONDS),
      resumeRewindShortSeconds = resumeRewindShortSeconds.coerceIn(MIN_RESUME_REWIND_SHORT_SECONDS, MAX_RESUME_REWIND_SHORT_SECONDS),
      resumeRewindLongSeconds = resumeRewindLongSeconds.coerceIn(MIN_RESUME_REWIND_LONG_SECONDS, MAX_RESUME_REWIND_LONG_SECONDS),
      chimeFadeVolume = chimeFadeVolume.coerceIn(MIN_CHIME_VOLUME, MAX_CHIME_VOLUME),
      chimeRearmVolume = chimeRearmVolume.coerceIn(MIN_CHIME_VOLUME, MAX_CHIME_VOLUME),
      defaultTimerScheduleStartMinute = defaultTimerScheduleStartMinute.coerceIn(MIN_SCHEDULE_MINUTE, MAX_SCHEDULE_MINUTE),
      defaultTimerScheduleEndMinute = defaultTimerScheduleEndMinute.coerceIn(MIN_SCHEDULE_MINUTE, MAX_SCHEDULE_MINUTE),
    )

  /** Whether [remainingSeconds] falls inside the fade-out window, i.e. a re-arm gesture is live. */
  fun isWithinFadeWindow(remainingSeconds: Long): Boolean = fadeEnabled && remainingSeconds in 1L..fadeSeconds.toLong()

  companion object {
    const val MIN_FADE_SECONDS = 1
    const val MAX_FADE_SECONDS = 60
    const val DEFAULT_FADE_SECONDS = 30

    const val MIN_REARM_EXTENSION_SECONDS = 30
    const val MAX_REARM_EXTENSION_SECONDS = 1800
    const val DEFAULT_REARM_EXTENSION_SECONDS = 300

    const val MIN_RESUME_REWIND_THRESHOLD_SECONDS = 0
    const val MAX_RESUME_REWIND_THRESHOLD_SECONDS = 900
    const val DEFAULT_RESUME_REWIND_THRESHOLD_SECONDS = 120

    const val MIN_RESUME_REWIND_SHORT_SECONDS = 0
    const val MAX_RESUME_REWIND_SHORT_SECONDS = 300
    const val DEFAULT_RESUME_REWIND_SHORT_SECONDS = 0

    const val MIN_RESUME_REWIND_LONG_SECONDS = 0
    const val MAX_RESUME_REWIND_LONG_SECONDS = 1800
    const val DEFAULT_RESUME_REWIND_LONG_SECONDS = 0

    const val MIN_CHIME_VOLUME = 0
    const val MAX_CHIME_VOLUME = 100
    const val DEFAULT_CHIME_VOLUME = 50

    const val MIN_SCHEDULE_MINUTE = 0
    const val MAX_SCHEDULE_MINUTE = 1439
    const val DEFAULT_SCHEDULE_START_MINUTE = 20 * 60 // 20:00
    const val DEFAULT_SCHEDULE_END_MINUTE = 8 * 60 // 08:00

    val Default = SleepTimerSettings()
  }
}

/** How much a re-arm extends a running duration timer by. */
enum class RearmExtensionMode {
  /** Extend by [SleepTimerSettings.rearmExtensionSeconds]. */
  FIXED,

  /** Extend by the timer's own original length (e.g. a 30 min timer re-arms to another 30 min). */
  MATCH_TIMER_DURATION,
}

/** How much to rewind when Play is pressed well after the sleep timer paused playback. */
enum class ResumeRewindLongMode {
  /** Rewind by [SleepTimerSettings.resumeRewindLongSeconds]. */
  FIXED,

  /** Rewind by whatever the re-arm extension currently resolves to ([RearmExtensionMode]-aware). */
  MATCH_EXTENSION,

  /**
   * Rewind by the timer's own original length for a duration timer, or to the start of the
   * current chapter for an "end of chapter/episode" timer (which has no fixed length).
   */
  MATCH_TIMER_DURATION,
}
