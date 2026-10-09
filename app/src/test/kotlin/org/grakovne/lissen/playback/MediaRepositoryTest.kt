package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.RearmExtensionMode
import org.grakovne.lissen.domain.ResumeRewindLongMode
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.bookmark
import org.grakovne.lissen.playback.PlaybackFixtures.descending
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.PlaybackFixtures.progress
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.grakovne.lissen.playback.autoskip.PlaybackSteps
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The repository over a fake player and an inline main thread: the orchestration runs for
 * real, and only the media session and the Android looper are replaced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {
  private class FakePlayer : PlayerConnection {
    val calls = mutableListOf<String>()
    lateinit var listener: PlayerConnection.Listener

    override var isConnected = true
    override var isPlaying = false
    override var currentMediaItemIndex = 0
    override var currentPositionMs = 0L

    override fun connect(
      listener: PlayerConnection.Listener,
      onConnected: () -> Unit,
    ) {
      this.listener = listener
      onConnected()
    }

    override fun whenConnected(action: () -> Unit) = action()

    override fun play(speed: Float) {
      calls.add("play")
    }

    override fun pause() {
      calls.add("pause")
    }

    override fun seekTo(
      mediaItemIndex: Int,
      positionMs: Long,
    ) {
      calls.add("seekTo($mediaItemIndex, $positionMs)")

      // a seek to the current position is masked and reported at once, as media3 does
      if (mediaItemIndex == currentMediaItemIndex && positionMs == currentPositionMs) return
      currentMediaItemIndex = mediaItemIndex
      currentPositionMs = positionMs
      listener.onPositionDiscontinuity(byPlayback = false)
    }

    override fun setPlaybackSpeed(speed: Float) {
      calls.add("speed($speed)")
    }

    override fun clear() {
      calls.add("clear")
    }

    override fun release() {
      calls.add("release")
    }
  }

  private class InlineMainThread : MainThread {
    val scheduled = mutableListOf<Runnable>()

    override fun run(action: () -> Unit) = action()

    override fun postDelayed(
      runnable: Runnable,
      delayMs: Long,
    ) {
      scheduled.add(runnable)
    }

    override fun cancel(runnable: Runnable) {
      scheduled.remove(runnable)
    }

    val polling: Boolean get() = scheduled.isNotEmpty()
  }

  private val player = FakePlayer()
  private val mainThread = InlineMainThread()
  private val eventBus = PlaybackEventBus()
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val autoSkipPreferences = mockk<AutoSkipPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val steps = PlaybackSteps()
  private val carConnected = MutableStateFlow(false)
  private val carConnectionMonitor =
    mockk<CarConnectionMonitor>(relaxed = true) { every { isConnected } returns carConnected }

  private lateinit var repository: MediaRepository

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    carConnected.value = false

    every { preferences.getPlaybackSpeed() } returns 1f
    every { preferences.getSeekTime() } returns SeekTime.Default
    every { preferences.getDefaultTimerOption() } returns null
    every { preferences.getPlayingItem() } returns null
    every { autoSkipPreferences.get(any()) } returns AutoSkipConfiguration.disabled

    repository =
      MediaRepository(
        preferences,
        autoSkipPreferences,
        mediaChannel,
        eventBus,
        DefaultTimerActivator(preferences),
        player,
        mainThread,
        steps,
        carConnectionMonitor,
      ).apply { ioDispatcher = UnconfinedTestDispatcher() }
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun position(
    mediaItemIndex: Int,
    positionMs: Long,
  ) = Player.PositionInfo(null, mediaItemIndex, null, null, mediaItemIndex, positionMs, positionMs, -1, -1)

  /** The book as the media session would report it: playing, ready, at its stored progress. */
  private fun playing(
    book: DetailedItem,
    playing: Boolean = false,
  ) {
    every { preferences.getPlayingItem() } returns book
    repository.registerPlayingBook(book)

    val progress = PlaybackGeometry.chapterProgress(book, book.progress?.currentTime ?: 0.0)
    player.currentMediaItemIndex = progress.index
    player.currentPositionMs = (progress.position * 1000).toLong()
    player.isPlaying = playing
    player.listener.onIsPlayingChanged(playing)
    player.calls.clear()
  }

  @Nested
  inner class ReorderPlayingItem {
    @Test
    fun `reorder keeps the listener on the same chapter and offset and rebuilds the queue`() =
      runTest {
        // 35s is 5s into c1; in the descending order c1 starts at 50s
        playing(podcast(progress = progress(35.0)))

        assertTrue(repository.reorderPlayingItem("podcast", descending()))

        val rebuilt = repository.playingBook.value!!
        assertEquals(listOf("c2", "c1", "c0"), rebuilt.chapters.map { it.id })
        assertEquals(listOf("file-c2", "file-c1", "file-c0"), rebuilt.files.map { it.id })
        assertEquals(55.0, rebuilt.progress?.currentTime)
        assertEquals(55.0, repository.totalPosition.value)
        assertEquals(1, repository.currentChapterIndex.value)
        assertEquals(5.0, repository.currentChapterPosition.value)
        assertEquals(listOf("pause"), player.calls)
        assertFalse(repository.isPlaybackReady.value)
        verify { preferences.savePlayingItem(rebuilt) }
        assertEquals(PlaybackCommand.PreparePlayback(rebuilt), eventBus.commands.first())
      }

    @Test
    fun `restored position never lands in the restart guard window`() =
      runTest {
        // 28s is 28s into c0; c0 ends up last (90..120s) and 118s would look like "finished,
        // start over" to the service, so the position is clamped to total minus the threshold
        playing(podcast(progress = progress(28.0)))

        repository.reorderPlayingItem("podcast", descending())

        assertEquals(
          115.0,
          repository.playingBook.value
            ?.progress
            ?.currentTime,
        )
        assertEquals(115.0, repository.totalPosition.value)
      }

    @Test
    fun `a second tap while the queue is rebuilding is ignored`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        player.calls.clear()

        assertFalse(repository.reorderPlayingItem("podcast", null))

        assertTrue(player.calls.isEmpty())
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `the default ordering over the canonical item does not touch playback`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)

        assertTrue(repository.reorderPlayingItem("podcast", null))

        assertTrue(player.calls.isEmpty())
        assertEquals(35.0, repository.totalPosition.value)
        assertTrue(repository.isPlaybackReady.value)
        assertTrue(repository.isPlaying.value)
      }

    @Test
    fun `reorder of another item than the one playing does nothing`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        assertFalse(repository.reorderPlayingItem("other", descending()))

        assertTrue(player.calls.isEmpty())
      }

    @Test
    fun `reorder does not depend on the item stored for the preferred library`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        every { preferences.getPlayingItem() } returns podcast(id = "preferred-library-item")

        assertTrue(repository.canReorderPlayingItem("podcast"))
        assertTrue(repository.reorderPlayingItem("podcast", descending()))
      }

    @Test
    fun `reorder resumes playback once the rebuilt queue is ready when it was playing`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)
        repository.reorderPlayingItem("podcast", descending())
        every { preferences.getPlayingItem() } returns repository.playingBook.value

        eventBus.emit(PlaybackEvent.PlaybackReady("podcast"))

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("pause", "play"), player.calls)
        // the seeded position is the truth: the controller still describes the previous queue
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `reorder stays paused once the rebuilt queue is ready when it was paused`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        every { preferences.getPlayingItem() } returns repository.playingBook.value

        eventBus.emit(PlaybackEvent.PlaybackReady("podcast"))

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("pause"), player.calls)
      }

    @Test
    fun `seeks are ignored while the queue is rebuilding`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        player.calls.clear()

        repository.forward()

        assertTrue(player.calls.isEmpty())
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `bookmarks move with the chapters they point at`() =
      runTest {
        coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
        coEvery { mediaChannel.provideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
        playing(podcast(progress = progress(35.0)))
        assertEquals(listOf(28.0), repository.bookmarks.first { it.isNotEmpty() }.map { it.totalPosition })

        repository.reorderPlayingItem("podcast", descending())

        // 28s into c0, which the descending order moves to the end of the item: at once from the
        // list in memory, and again once the stored list is re-read
        assertEquals(listOf(118.0), repository.bookmarks.value.map { it.totalPosition })
        assertEquals(listOf(118.0), repository.bookmarks.first { it.isNotEmpty() }.map { it.totalPosition })
      }
  }

  @Nested
  inner class PlaybackReadiness {
    @Test
    fun `failed fetch reports that preparation did not start`() =
      runTest {
        coEvery { mediaChannel.fetchBook("missing", null) } returns OperationResult.Error(OperationError.NetworkError)

        assertFalse(repository.preparePlayback("missing"))
        assertTrue(repository.mediaPreparingError.value)
      }

    @Test
    fun `ready event applies to the book named by the service`() =
      runTest {
        playing(podcast())
        repository.clearPreparedItem()

        eventBus.emit(PlaybackEvent.PlaybackReady("another-book"))
        assertFalse(repository.isPlaybackReady.value)

        eventBus.emit(PlaybackEvent.PlaybackReady("podcast"))
        assertTrue(repository.isPlaybackReady.value)
      }

    @Test
    fun `a book whose queue is still being built waits for the service`() =
      runTest {
        playing(podcast())
        repository.clearPreparedItem()
        repository.prepareAndPlay(podcast(id = "next"))

        // the same book again, as openBook does right after preparing it
        repository.prepareAndPlay(repository.playingBook.value!!)

        assertFalse(repository.isPlaybackReady.value)
        assertTrue(player.calls.isEmpty())

        eventBus.emit(PlaybackEvent.PlaybackReady("next"))

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("play"), player.calls)
      }

    @Test
    fun `a book whose queue is already built is ready at once`() =
      runTest {
        playing(podcast())
        repository.clearPreparedItem()

        repository.prepareAndPlay(repository.playingBook.value!!)

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("play"), player.calls)

        eventBus.emit(PlaybackEvent.PlaybackReady("podcast"))

        assertEquals(listOf("play"), player.calls)
      }
  }

  @Nested
  inner class PlayerErrors {
    @Test
    fun `a player error flags the preparation and stops everything in flight`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)
        repository.clearPreparedItem()
        repository.prepareAndPlay(podcast(id = "next"))
        assertTrue(player.calls.isEmpty())

        player.listener.onError(mockk<PlaybackException>(relaxed = true))

        assertTrue(repository.mediaPreparingError.value)
        assertFalse(repository.isPlaying.value)
        assertFalse(mainThread.polling)
        // the deferred autoplay is dropped: readiness will not come
        every { preferences.getPlayingItem() } returns repository.playingBook.value
        eventBus.emit(PlaybackEvent.PlaybackReady("next"))
        assertFalse(player.calls.contains("play"))
      }

    @Test
    fun `clearing the prepared item drops the deferred autoplay and the error`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.clearPreparedItem()
        repository.prepareAndPlay(podcast(id = "next"))
        player.listener.onError(mockk<PlaybackException>(relaxed = true))

        repository.clearPreparedItem()

        assertFalse(repository.mediaPreparingError.value)
        assertFalse(repository.isPlaybackReady.value)
      }
  }

  @Nested
  inner class PlayingBookCleanup {
    @Test
    fun `clearing the playing book clears preparation and timer state`() =
      runTest {
        val book = podcast(progress = progress(35.0))
        val next = podcast(id = "next")
        playing(book, playing = true)
        repository.clearPreparedItem()
        repository.prepareAndPlay(next)
        assertEquals(PlaybackCommand.PreparePlayback(next), eventBus.commands.first())
        player.listener.onError(mockk<PlaybackException>(relaxed = true))

        val timer = DurationTimerOption(5)
        repository.updateTimer(timer)
        assertEquals(PlaybackCommand.SetTimer(300.0, timer), eventBus.commands.first())

        repository.clearPlayingBook()

        assertEquals(null, repository.playingBook.value)
        assertEquals(null, repository.timerOption.value)
        assertFalse(repository.mediaPreparingError.value)
        assertFalse(repository.isPlaybackReady.value)
        assertFalse(repository.isPlaying.value)
        assertEquals(listOf("clear"), player.calls)
        assertEquals(PlaybackCommand.CancelTimer, eventBus.commands.first())
        verify { preferences.clearPlayingItem(next.id) }
      }
  }

  @Nested
  inner class ProgressPolling {
    @Test
    fun `progress is polled only while playing`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        assertFalse(mainThread.polling)

        player.listener.onIsPlayingChanged(true)
        assertTrue(mainThread.polling)

        player.listener.onIsPlayingChanged(false)
        assertFalse(mainThread.polling)
      }

    @Test
    fun `repeated play events keep a single poll`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repeat(3) { player.listener.onIsPlayingChanged(true) }

        assertEquals(1, mainThread.scheduled.size)
      }

    @Test
    fun `a poll reads the position back from the player`() =
      runTest {
        playing(podcast(progress = progress(0.0)))
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 5_000L

        player.listener.onPositionDiscontinuity(byPlayback = false)

        assertEquals(75.0, repository.totalPosition.value)
        assertEquals(2, repository.currentChapterIndex.value)
        assertEquals(5.0, repository.currentChapterPosition.value)
        assertEquals(50.0, repository.currentChapterDuration.value)
      }

    @Test
    fun `an ended item is rewound and paused`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)

        player.listener.onEnded()

        assertEquals(listOf("seekTo(0, 0)", "pause"), player.calls)
      }
  }

  @Nested
  inner class Seeking {
    @Test
    fun `forward seeks by the preferred step inside the chapter`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.forward()

        assertEquals(listOf("seekTo(1, 35000)"), player.calls)
      }

    @Test
    fun `a chapter is entered at its start`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.setChapter(2)

        assertEquals(listOf("seekTo(2, 0)"), player.calls)
      }

    @Test
    fun `a chapter that does not exist is not entered`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.setChapter(7)

        assertTrue(player.calls.isEmpty())
      }

    @Test
    fun `an episode timer follows the seek`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        repository.setTotalPosition(60.0)

        assertEquals(PlaybackCommand.SetTimer(10.0, CurrentEpisodeTimerOption), eventBus.commands.first())
      }

    @Test
    fun `an episode timer ends where the auto-skipped outro begins`() =
      runTest {
        every { autoSkipPreferences.get("podcast") } returns AutoSkipConfiguration(introSeconds = 0, outroSeconds = 10)
        playing(podcast(progress = progress(35.0)))

        repository.updateTimer(CurrentEpisodeTimerOption)

        assertEquals(PlaybackCommand.SetTimer(25.0, CurrentEpisodeTimerOption), eventBus.commands.first())
      }

    @Test
    fun `the forward step marks its seek as the player's own, a scrub does not`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        // 5 s into c1, the step lands 30 s later
        repository.forward()
        assertTrue(steps.take(position(1, 35_000L)))

        repository.setChapterPosition(20.0)
        assertFalse(steps.take(position(1, 20_000L)))

        repository.rewind()
        assertFalse(steps.take(position(0, 0L)))
      }

    @Test
    fun `a seek discontinuity re-arms an episode timer from the new position`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        // the auto-skip moved playback 5 s into c2 by itself
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 5_000L
        player.listener.onPositionDiscontinuity(byPlayback = false)

        assertEquals(PlaybackCommand.SetTimer(45.0, CurrentEpisodeTimerOption), eventBus.commands.first())
      }

    @Test
    fun `playback running on into the next chapter leaves the episode timer to expire there`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        // the countdown was behind: c1 ran out first
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 0L
        player.listener.onPositionDiscontinuity(byPlayback = true)

        assertEquals(2, repository.currentChapterIndex.value)
        val minutes = DurationTimerOption(5)
        repository.updateTimer(minutes)
        assertEquals(PlaybackCommand.SetTimer(300.0, minutes), eventBus.commands.first(), "no count over all of c2 was sent")
      }

    @Test
    fun `a refresh without an episode timer sends nothing`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        val minutes = DurationTimerOption(5)
        repository.refreshTimer()
        repository.updateTimer(minutes)

        assertEquals(PlaybackCommand.SetTimer(300.0, minutes), eventBus.commands.first())
      }

    @Test
    fun `changing the outro re-arms an episode timer`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        every { autoSkipPreferences.get("podcast") } returns AutoSkipConfiguration(introSeconds = 0, outroSeconds = 15)
        repository.refreshTimer()

        assertEquals(PlaybackCommand.SetTimer(20.0, CurrentEpisodeTimerOption), eventBus.commands.first())
      }
  }

  @Nested
  inner class SleepTimerRearm {
    @Test
    fun `a duration timer re-arm extends the countdown by the configured amount`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true, rearmExtensionSeconds = 300)
        playing(podcast(progress = progress(0.0)))

        val option = DurationTimerOption(5)
        repository.updateTimer(option)
        assertEquals(PlaybackCommand.SetTimer(300.0, option), eventBus.commands.first())

        eventBus.emit(PlaybackEvent.TimerTick(20L))

        assertTrue(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
        assertEquals(PlaybackCommand.SetTimer(320.0, option), eventBus.commands.first())
        assertEquals(PlaybackEvent.TimerRearmed, eventBus.events.first())
      }

    @Test
    fun `a duration timer re-arm can extend by the timer's own original length instead`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            fadeEnabled = true,
            fadeSeconds = 30,
            rearmEnabled = true,
            rearmExtensionMode = RearmExtensionMode.MATCH_TIMER_DURATION,
            rearmExtensionSeconds = 300,
          )
        playing(podcast(progress = progress(0.0)))

        val option = DurationTimerOption(2)
        repository.updateTimer(option)
        assertEquals(PlaybackCommand.SetTimer(120.0, option), eventBus.commands.first())

        eventBus.emit(PlaybackEvent.TimerTick(20L))

        assertTrue(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
        // 20s remaining + 120s (the timer's own 2-minute length), not the unused 300s fixed amount
        assertEquals(PlaybackCommand.SetTimer(140.0, option), eventBus.commands.first())
      }

    @Test
    fun `re-arm does nothing when the feature is disabled`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = false)
        playing(podcast(progress = progress(0.0)))

        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()
        eventBus.emit(PlaybackEvent.TimerTick(20L))

        assertFalse(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
      }

    @Test
    fun `re-arm does nothing outside the fade window`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true)
        playing(podcast(progress = progress(0.0)))

        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()
        eventBus.emit(PlaybackEvent.TimerTick(45L))

        assertFalse(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
      }

    @Test
    fun `re-arm respects the per-trigger toggle`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true, rearmViaShake = false)
        playing(podcast(progress = progress(0.0)))

        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()
        eventBus.emit(PlaybackEvent.TimerTick(20L))

        assertFalse(repository.rearmTimer(RearmTrigger.SHAKE))
      }

    @Test
    fun `a second trigger shortly after a successful re-arm does not re-arm again`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true)
        playing(podcast(progress = progress(0.0)))
        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()
        eventBus.emit(PlaybackEvent.TimerTick(20L))

        var clock = 10_000L
        repository.elapsedTimeMillis = { clock }

        assertTrue(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))

        // _timerRemaining is still the stale, within-the-window value: no fresh TimerTick has
        // arrived yet, matching the real lag before PlaybackTimer's SetTimer command is processed
        clock += 1_500L
        assertFalse(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON), "too soon after the last re-arm")

        clock += 1_000L // now 2.5s after the first re-arm: past the debounce window
        assertTrue(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
      }

    @Test
    fun `re-arm with no timer running does nothing`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true)
        playing(podcast(progress = progress(0.0)))

        assertFalse(repository.rearmTimer(RearmTrigger.HEADPHONE_BUTTON))
      }

    @Test
    fun `an episode timer re-arm lets the current chapter finish and re-arms for the next one`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30, rearmEnabled = true)
        // 5s into c1 (30..70s): remaining in chapter is 35s
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        // the fade window opens with 20s left in c1
        eventBus.emit(PlaybackEvent.TimerTick(20L))
        assertTrue(repository.rearmTimer(RearmTrigger.SHAKE))
        assertEquals(PlaybackCommand.SuppressNextChapterStop, eventBus.commands.first())
        assertEquals(PlaybackEvent.TimerRearmed, eventBus.events.first())

        // playback runs on into c2 at its very start, as the auto-transition reports it
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 0L
        player.listener.onPositionDiscontinuity(byPlayback = true)

        assertEquals(2, repository.currentChapterIndex.value)
        // c2 spans 70..120s: a fresh 50s countdown is armed for it, where an un-rearmed
        // transition would have sent nothing at all (PlaybackTimer just expires on its own)
        assertEquals(PlaybackCommand.SetTimer(50.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        // the re-arm is one-shot: the next auto-transition is not re-armed again
        val minutes = DurationTimerOption(5)
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 10_000L
        player.listener.onPositionDiscontinuity(byPlayback = true)
        repository.updateTimer(minutes)
        assertEquals(PlaybackCommand.SetTimer(300.0, minutes), eventBus.commands.first(), "no stray re-arm survived into c2")
      }
  }

  @Nested
  inner class TimerPauseRewind {
    @BeforeEach
    fun freezeClock() {
      // deterministic "quick resume" by default; individual tests bump this past the threshold
      repository.elapsedTimeMillis = { 0L }
    }

    private suspend fun pauseByTimer() {
      eventBus.emit(PlaybackEvent.TimerExpired)
      player.isPlaying = false
      player.listener.onIsPlayingChanged(false)
    }

    private fun resume() {
      player.isPlaying = true
      player.listener.onIsPlayingChanged(true)
    }

    @Test
    fun `the short rewind happens at the pause, not at the resume`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns SleepTimerSettings(resumeRewindShortSeconds = 20)
        playing(podcast(progress = progress(35.0)), playing = true)

        eventBus.emit(PlaybackEvent.TimerExpired)

        // 35s - 20s = 15s, inside c0 (0..30s)
        assertEquals(listOf("pause", "seekTo(0, 15000)"), player.calls)

        player.calls.clear()
        player.isPlaying = false
        player.listener.onIsPlayingChanged(false)
        resume()

        assertTrue(player.calls.none { it.startsWith("seekTo") }, "a quick resume finds the position already rewound")
      }

    @Test
    fun `a zero short rewind setting never seeks at the pause`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns SleepTimerSettings(resumeRewindShortSeconds = 0)
        playing(podcast(progress = progress(35.0)), playing = true)

        eventBus.emit(PlaybackEvent.TimerExpired)

        assertEquals(listOf("pause"), player.calls)
      }

    @Test
    fun `a resume inside the threshold cancels the pending long rewind`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(resumeRewindThresholdSeconds = 120, resumeRewindShortSeconds = 20, resumeRewindLongSeconds = 25)
        playing(podcast(progress = progress(35.0)), playing = true)

        pauseByTimer()
        val delayed = mainThread.scheduled.single()
        player.calls.clear()

        repository.elapsedTimeMillis = { 119_000L }
        resume()

        assertTrue(player.calls.none { it.startsWith("seekTo") })
        assertFalse(mainThread.scheduled.contains(delayed), "the delayed long rewind is cancelled")
      }

    @Test
    fun `the long rewind tops up the short one when the threshold passes while paused`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            resumeRewindThresholdSeconds = 120,
            resumeRewindShortSeconds = 20,
            resumeRewindLongMode = ResumeRewindLongMode.FIXED,
            resumeRewindLongSeconds = 25,
          )
        playing(podcast(progress = progress(35.0)), playing = true)

        pauseByTimer()
        player.calls.clear()

        mainThread.scheduled.single().run()

        // already at 15s after the short rewind; 25s in total is 10s
        assertEquals(listOf("seekTo(0, 10000)"), player.calls)

        player.calls.clear()
        repository.elapsedTimeMillis = { 121_000L }
        resume()
        assertTrue(player.calls.none { it.startsWith("seekTo") }, "the top-up must not repeat at the resume")
      }

    @Test
    fun `a resume past the threshold applies the long rewind when the delayed one never ran`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            resumeRewindThresholdSeconds = 120,
            resumeRewindShortSeconds = 20,
            resumeRewindLongMode = ResumeRewindLongMode.FIXED,
            resumeRewindLongSeconds = 25,
          )
        playing(podcast(progress = progress(35.0)), playing = true)

        pauseByTimer()
        val delayed = mainThread.scheduled.single()
        player.calls.clear()

        repository.elapsedTimeMillis = { 121_000L }
        resume()

        assertEquals(listOf("seekTo(0, 10000)"), player.calls)
        assertFalse(mainThread.scheduled.contains(delayed), "the delayed job is dropped once applied")
      }

    @Test
    fun `clearing the prepared item keeps the pending long rewind for the same book`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(resumeRewindThresholdSeconds = 120, resumeRewindShortSeconds = 20, resumeRewindLongSeconds = 25)
        playing(podcast(progress = progress(35.0)), playing = true)

        pauseByTimer()
        repository.clearPreparedItem()
        player.calls.clear()

        repository.elapsedTimeMillis = { 121_000L }
        resume()

        assertEquals(listOf("seekTo(0, 10000)"), player.calls)
      }

    @Test
    fun `rewind-on-pause that already moved the playhead is not doubled by the short rewind`() =
      runTest {
        every { preferences.getRewindOnPause() } returns RewindOnPauseSettings(enabled = true, seconds = 20)
        every { preferences.getSleepTimerSettings() } returns SleepTimerSettings(resumeRewindShortSeconds = 20)
        playing(podcast(progress = progress(35.0)), playing = true)

        eventBus.emit(PlaybackEvent.TimerExpired)

        assertEquals(listOf("pause"), player.calls)
      }

    @Test
    fun `the long rewind can match the re-arm extension amount`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            resumeRewindThresholdSeconds = 120,
            resumeRewindLongMode = ResumeRewindLongMode.MATCH_EXTENSION,
            rearmExtensionSeconds = 20,
          )
        playing(podcast(progress = progress(35.0)), playing = true)

        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()

        pauseByTimer()
        player.calls.clear()
        mainThread.scheduled.single().run()

        // 35s - 20s (the re-arm extension) = 15s, inside c0
        assertEquals(listOf("seekTo(0, 15000)"), player.calls)
      }

    @Test
    fun `the long rewind can match a duration timer's own length`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            resumeRewindThresholdSeconds = 120,
            resumeRewindLongMode = ResumeRewindLongMode.MATCH_TIMER_DURATION,
          )
        playing(podcast(progress = progress(100.0)), playing = true)

        repository.updateTimer(DurationTimerOption(1))
        eventBus.commands.first()

        pauseByTimer()
        player.calls.clear()
        mainThread.scheduled.single().run()

        // 100s - 60s (the 1 minute timer's own length) = 40s, inside c1 (30..70s, 10s in)
        assertEquals(listOf("seekTo(1, 10000)"), player.calls)
      }

    @Test
    fun `the long rewind matching an episode timer rewinds to the chapter start`() =
      runTest {
        every { preferences.getSleepTimerSettings() } returns
          SleepTimerSettings(
            resumeRewindThresholdSeconds = 120,
            resumeRewindLongMode = ResumeRewindLongMode.MATCH_TIMER_DURATION,
          )
        // 5s into c1 (30..70s)
        playing(podcast(progress = progress(35.0)), playing = true)

        repository.updateTimer(CurrentEpisodeTimerOption)
        eventBus.commands.first()

        pauseByTimer()
        player.calls.clear()
        mainThread.scheduled.single().run()

        assertEquals(listOf("seekTo(1, 0)"), player.calls, "must rewind to the start of c1, not a fixed offset")
      }
  }

  @Nested
  inner class SleepTimerDisabledWhileDriving {
    @Test
    fun `setting a timer while connected to a car does nothing`() =
      runTest {
        carConnected.value = true
        playing(podcast(progress = progress(0.0)))

        repository.updateTimer(DurationTimerOption(5))

        assertNull(repository.timerOption.value)
      }

    @Test
    fun `connecting to a car cancels an already-running timer`() =
      runTest {
        playing(podcast(progress = progress(0.0)))
        val option = DurationTimerOption(5)
        repository.updateTimer(option)
        assertEquals(PlaybackCommand.SetTimer(300.0, option), eventBus.commands.first())

        carConnected.value = true

        assertNull(repository.timerOption.value)
        assertEquals(PlaybackCommand.CancelTimer, eventBus.commands.first())
      }

    @Test
    fun `disconnecting from a car does not bring the timer back`() =
      runTest {
        playing(podcast(progress = progress(0.0)))
        repository.updateTimer(DurationTimerOption(5))
        eventBus.commands.first()

        carConnected.value = true
        eventBus.commands.first()
        carConnected.value = false

        assertNull(repository.timerOption.value)
      }
  }
}
