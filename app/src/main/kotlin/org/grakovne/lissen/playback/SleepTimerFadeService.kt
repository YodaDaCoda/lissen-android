package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fades the volume to silence before the sleep timer pauses playback. There is one ramp: the
 * first tick inside the window captures the volume and moves it linearly to zero at expiry.
 * Later ticks never change its timing. The volume is never raised while playing. After an
 * expiry it stays at zero until the player stops; cancelling the timer restores it at once.
 * The ramp tracks actual listening time like the underlying countdown does: pausing mid-fade
 * freezes it in place, resuming continues the same ramp - silently, with no chime or re-arm.
 */
@Singleton
class SleepTimerFadeService
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val playbackEventBus: PlaybackEventBus,
    private val preferences: PlaybackPreferences,
    private val chimePlayer: SleepTimerChimePlayer,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fadeJob: Job? = null
    private var fading = false
    private var awaitingRestore = false
    private var originalVolume = 1f

    // mirrors the player's play/pause state so the ramp freezes while paused, same as the
    // underlying countdown (PlaybackTimer) - resuming continues the same ramp, no re-trigger
    private val isPlayingState = MutableStateFlow(true)

    private val playerListener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          isPlayingState.value = isPlaying
          if (!isPlaying) {
            restoreAfterPlaybackStopped()
          }
        }
      }

    override fun onCreate() {
      player.addListener(playerListener)

      scope.launch {
        playbackEventBus.events.collect { event ->
          when (event) {
            is PlaybackEvent.TimerTick -> {
              onTick(event.remainingSeconds)
            }

            PlaybackEvent.TimerExpired -> {
              onTimerExpired()
            }

            PlaybackEvent.TimerCancelled -> {
              onTimerCancelled()
            }

            PlaybackEvent.TimerRearmed -> {
              val settings = preferences.getSleepTimerSettings()
              if (settings.chimeOnRearm) chimePlayer.playRearm(settings.chimeRearmVolume / 100f)
            }

            else -> {}
          }
        }
      }
    }

    private fun onTick(remainingSeconds: Long) {
      if (fading) return

      val settings = preferences.getSleepTimerSettings()
      if (!settings.isWithinFadeWindow(remainingSeconds)) return

      startFade(remainingSeconds, settings)
    }

    private fun startFade(
      remainingSeconds: Long,
      settings: SleepTimerSettings,
    ) {
      fading = true
      originalVolume = player.volume

      playbackEventBus.emit(PlaybackEvent.TimerFadeStarted)
      if (settings.chimeOnFadeStart) chimePlayer.playFadeStart(settings.chimeFadeVolume / 100f)

      val durationMillis = remainingSeconds * MILLIS_PER_SECOND
      Timber.d("Sleep timer fade started: volume=$originalVolume, durationMillis=$durationMillis")

      fadeJob =
        scope.launch {
          var elapsedMillis = 0L

          while (elapsedMillis < durationMillis) {
            // paused: suspend here (no polling) until playback resumes, then pick the ramp
            // back up from the same elapsed point - no event, so no chime or re-arm
            isPlayingState.first { it }

            delay(FADE_STEP_MILLIS)
            if (!isPlayingState.value) continue

            elapsedMillis += FADE_STEP_MILLIS
            player.volume = fadeVolumeAt(originalVolume, elapsedMillis, durationMillis)
          }

          player.volume = 0f
        }
    }

    private fun onTimerExpired() {
      fadeJob?.cancel()
      fadeJob = null

      if (!fading) return

      // Force zero volume at the exact moment of the pause, even if the ramp is slightly behind.
      player.volume = 0f
      fading = false

      if (player.isPlaying) {
        awaitingRestore = true
      } else {
        restoreVolume()
      }
    }

    private fun onTimerCancelled() {
      fadeJob?.cancel()
      fadeJob = null

      // after an expiry `fading` is already cleared, so a late cancellation keeps the silence
      if (fading) {
        fading = false
        restoreVolume()
      }
    }

    private fun restoreAfterPlaybackStopped() {
      if (!awaitingRestore) return

      restoreVolume()
    }

    private fun restoreVolume() {
      awaitingRestore = false
      player.volume = originalVolume
      Timber.d("Sleep timer fade reverted: volume=$originalVolume")
    }

    companion object {
      private const val MILLIS_PER_SECOND = 1000L
      private const val FADE_STEP_MILLIS = 50L
    }
  }

internal fun fadeVolumeAt(
  originalVolume: Float,
  elapsedMillis: Long,
  durationMillis: Long,
): Float =
  if (durationMillis <= 0L) {
    0f
  } else {
    val fraction = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    (originalVolume * (1f - fraction)).coerceIn(0f, 1f)
  }
