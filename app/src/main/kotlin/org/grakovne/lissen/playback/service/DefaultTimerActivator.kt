package org.grakovne.lissen.playback.service

import androidx.annotation.VisibleForTesting
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTimerActivator
  @Inject
  constructor(
    private val preferences: PlaybackPreferences,
  ) {
    private var pending = true

    @VisibleForTesting
    internal var nowMinuteOfDay: () -> Int = { LocalTime.now().toSecondOfDay() / 60 }

    fun onPlaybackStarted(applyTimer: (TimerOption) -> Unit) {
      if (!pending) return
      pending = false

      val option = preferences.getDefaultTimerOption() ?: return
      val settings = preferences.getSleepTimerSettings()

      if (settings.defaultTimerScheduleEnabled &&
        !isWithinMinuteWindow(nowMinuteOfDay(), settings.defaultTimerScheduleStartMinute, settings.defaultTimerScheduleEndMinute)
      ) {
        return
      }

      applyTimer(option)
    }

    fun onTimerManuallySet() {
      pending = false
    }

    fun onTimerExpired() {
      pending = true
    }

    fun onNewBookPrepared() {
      pending = true
    }
  }

/**
 * Whether [minuteOfDay] falls within [startMinute]..[endMinute), wrapping past midnight when
 * [startMinute] is after [endMinute] (e.g. 20:00..08:00 covers the overnight window).
 */
internal fun isWithinMinuteWindow(
  minuteOfDay: Int,
  startMinute: Int,
  endMinute: Int,
): Boolean =
  if (startMinute <= endMinute) {
    minuteOfDay in startMinute until endMinute
  } else {
    minuteOfDay >= startMinute || minuteOfDay < endMinute
  }
