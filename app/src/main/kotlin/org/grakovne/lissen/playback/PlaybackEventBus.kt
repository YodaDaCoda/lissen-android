package org.grakovne.lissen.playback

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.TimerOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEventBus
  @Inject
  constructor() {
    private val _events = MutableSharedFlow<PlaybackEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private val _commands = Channel<PlaybackCommand>(Channel.BUFFERED)
    val commands: Flow<PlaybackCommand> = _commands.receiveAsFlow()

    fun emit(event: PlaybackEvent) {
      _events.tryEmit(event)
    }

    fun send(command: PlaybackCommand) {
      _commands.trySendBlocking(command)
    }
  }

sealed class PlaybackEvent {
  data class PlaybackReady(
    val bookId: String,
  ) : PlaybackEvent()

  data object TimerExpired : PlaybackEvent()

  data object TimerCancelled : PlaybackEvent()

  data class TimerTick(
    val remainingSeconds: Long,
  ) : PlaybackEvent()

  /** The fade-out ramp just started; the re-arm window (headphone button / shake) is now open. */
  data object TimerFadeStarted : PlaybackEvent()

  /** A re-arm gesture (headphone button / shake) cancelled the impending stop. */
  data object TimerRearmed : PlaybackEvent()
}

sealed class PlaybackCommand {
  data class PreparePlayback(
    val book: DetailedItem,
  ) : PlaybackCommand()

  data class SetTimer(
    val delay: Double,
    val option: TimerOption,
  ) : PlaybackCommand()

  data object CancelTimer : PlaybackCommand()

  /** A single upcoming auto-transition stop (end-of-chapter timer) must not expire the timer. */
  data object SuppressNextChapterStop : PlaybackCommand()
}
