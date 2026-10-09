package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimer
  @Inject
  constructor(
    private val playbackEventBus: PlaybackEventBus,
    private val exoPlayer: ExoPlayer,
  ) {
    private var option: TimerOption? = null
    private var timer: Countdown? = null

    /** The full length a duration timer would currently run if (re)started from scratch - see [restartAtCurrentLimit]. */
    private var currentLimitMillis: Long = 0L

    /** Set while paused; 0 when not. Used to tell a real pause from a brief rebuffer stall. */
    private var pausedAtMillis: Long = 0L

    /** Set by a chapter-skip re-arm; consumed by the very next auto-transition, which is let through unstopped. */
    private var suppressNextAutoTransitionExpire = false

    @VisibleForTesting
    internal var countdownFactory =
      CountdownFactory { totalMillis, intervalMillis, onTickSeconds, onFinished ->
        SuspendableCountDownTimer(totalMillis, intervalMillis, onTickSeconds, onFinished).also { it.start() }
      }

    @VisibleForTesting
    internal var elapsedTimeMillis: () -> Long = System::currentTimeMillis

    private val playerListener =
      object : Player.Listener {
        // every timer tracks actual listening time, not wall-clock time: pausing playback
        // pauses the countdown (and, for a duration timer, the fade service's own ramp - see
        // SleepTimerFadeService). A duration timer resumed after a real pause restarts fresh at
        // its current limit rather than continuing from wherever it had counted down to, so
        // resuming playback doesn't fire the fade chime far sooner than expected; a brief
        // rebuffer stall (isPlaying flickering false/true while still "playing" in intent) is
        // too short to count as a real pause and just continues as before. Either way this is
        // silent: no chime, no re-arm.
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          val currentTimer = timer ?: return

          when (isPlaying) {
            true -> {
              val pausedFor = if (pausedAtMillis > 0L) elapsedTimeMillis() - pausedAtMillis else 0L
              timer =
                if (option is DurationTimerOption && pausedFor >= MIN_PAUSE_FOR_RESET_MILLIS) {
                  restartAtCurrentLimit()
                } else {
                  currentTimer.resume()
                }
              pausedAtMillis = 0L
            }

            false -> {
              pausedAtMillis = elapsedTimeMillis()
              currentTimer.pause()
            }
          }
        }

        // the countdown was armed from a position polled a moment earlier, so it can be behind
        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          if (option != CurrentEpisodeTimerOption) return
          if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
          if (newPosition.mediaItemIndex == oldPosition.mediaItemIndex) return

          if (suppressNextAutoTransitionExpire) {
            // a chapter-skip re-arm let this one boundary pass; MediaRepository arms a fresh
            // timer for the new chapter right after this callback
            suppressNextAutoTransitionExpire = false
            return
          }

          if (timer == null) return
          expire()
        }
      }

    @OptIn(UnstableApi::class)
    fun startTimer(
      delayInSeconds: Double,
      option: TimerOption,
    ) {
      Timber.d("Starting timer: ${delayInSeconds.toInt()}s, option=$option")
      stopTimer()
      // before the expiry below, which reads it
      this.option = option

      val totalMillis = (delayInSeconds * 1000).toLong()
      currentLimitMillis = totalMillis
      if (totalMillis <= 0L) {
        expire()
        return
      }

      broadcastRemaining(delayInSeconds.toLong())

      timer = createCountdown(totalMillis)

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      if (exoPlayer.isPlaying.not()) {
        timer?.pause()
      }
    }

    val isEpisodeTimerRunning: Boolean
      get() = timer != null && option == CurrentEpisodeTimerOption

    /** True from the pause of an expiring "end of episode" timer until every listener has seen that pause. */
    var isEpisodeTimerExpiring: Boolean = false
      private set

    // cleared on onEvents, which comes after every listener saw onPlayWhenReadyChanged: a pause
    // made inside a player callback is delivered only after pause() has returned
    private val expiryListener =
      object : Player.Listener {
        override fun onEvents(
          player: Player,
          events: Player.Events,
        ) {
          if (!events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) return

          isEpisodeTimerExpiring = false
          exoPlayer.removeListener(this)
        }
      }

    /**
     * A chapter-skip re-arm: drops the stale countdown (sized for the chapter about to end) so
     * it can't expire on its own, and arms [suppressNextAutoTransitionExpire] so the imminent
     * auto-transition doesn't stop playback either. The exoPlayer listener stays attached, since
     * the option is still [CurrentEpisodeTimerOption] and future transitions still matter.
     */
    fun suppressNextChapterStop() {
      if (option != CurrentEpisodeTimerOption) return

      suppressNextAutoTransitionExpire = true
      timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
      timer?.stop()
      timer = null
    }

    private fun createCountdown(totalMillis: Long): Countdown =
      countdownFactory.create(totalMillis, 500L, { seconds -> broadcastRemaining(seconds) }, { expire() })

    /**
     * Drops the paused, partially-consumed countdown and starts a fresh one at [currentLimitMillis] -
     * the length the timer would have if (re)started right now. Emits [PlaybackEvent.TimerCancelled]
     * first so the fade service restores any volume it had ramped down, same as any other replace.
     */
    private fun restartAtCurrentLimit(): Countdown? {
      timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
      timer?.stop()

      if (currentLimitMillis <= 0L) return null

      broadcastRemaining(currentLimitMillis / 1000)
      return createCountdown(currentLimitMillis)
    }

    private fun expire() {
      Timber.d("Timer expired, pausing and broadcasting")
      // an expiry is not a cancellation: no TimerCancelled event, or the fade would undo itself at the pause
      timer?.stop()
      timer = null

      // only a player that is going to deliver the pause, or nothing would clear the flag
      if (option == CurrentEpisodeTimerOption && exoPlayer.playWhenReady) {
        isEpisodeTimerExpiring = true
        exoPlayer.addListener(expiryListener)
      }

      // pause before the event: auto-skip must see the player paused at this exact moment
      exoPlayer.pause()
      playbackEventBus.emit(PlaybackEvent.TimerExpired)
      stopTimer()
    }

    private fun broadcastRemaining(seconds: Long) {
      playbackEventBus.emit(PlaybackEvent.TimerTick(seconds))
    }

    fun stopTimer() {
      Timber.d("Stopping timer")
      timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
      timer?.stop()
      timer = null

      exoPlayer.removeListener(playerListener)
    }

    private companion object {
      /** Pauses shorter than this are a quick pause/resume (MediaRepository re-arms); longer ones reset the timer. Keep equal to MediaRepository.REARM_DEBOUNCE_MILLIS. */
      const val MIN_PAUSE_FOR_RESET_MILLIS = 2_000L
    }
  }
