# Sleep timer subsystem

Upstream has a basic sleep timer; this fork adds fade-out, shake/headphone-button re-arm, a
resume-rewind, a scheduled default timer, and car-mode disable. All settings live in one
Moshi-serializable `SleepTimerSettings` (`domain/SleepTimerSettings.kt`), with `.clamped()`
enforcing bounds on load/save.

## Component map

| Component | Role |
|---|---|
| `playback/service/PlaybackTimer.kt` | Owns the actual countdown loop (`SuspendableCountDownTimer` via injectable `countdownFactory`). Emits `TimerTick` every 500ms. |
| `playback/PlaybackEventBus.kt` | `SharedFlow<PlaybackEvent>` (replay=1) + `Channel<PlaybackCommand>`, decoupling the timer from the fade/shake components. |
| `playback/SleepTimerFadeService.kt` | Runs the volume fade ramp off `TimerTick`, plays the fade chime, restores volume on cancel/expire. |
| `playback/SleepTimerChimePlayer.kt` | `SoundPool` wrapper (media/sonification attrs — routes through Bluetooth correctly) for the two chime sounds. |
| `playback/ShakeRearmDetector.kt` | `RunningComponent`; accelerometer listener registered **only** between `TimerFadeStarted` and `TimerExpired`/`TimerCancelled`. |
| `playback/ShakeRearmModule.kt` | Hilt `@IntoSet` multibind of the detector into the `RunningComponent` set. |
| `playback/service/DefaultTimerActivator.kt` | Auto-applies a configured default timer once per playback session, optionally within a daily schedule window. |
| `playback/MediaRepository.kt` | `rearmTimer(trigger)` — the actual re-arm logic for both shake and headphone-button triggers; also owns car-mode gating. |
| `playback/CarConnectionMonitor.kt` | Wraps `androidx.car.app.connection.CarConnection`; `isConnected: StateFlow<Boolean>`. |

## Fade → re-arm flow

1. `PlaybackTimer` ticks every 500ms via the event bus.
2. `SleepTimerFadeService` checks `settings.isWithinFadeWindow(remainingSeconds)`
   (`fadeEnabled && remaining in 1..fadeSeconds`); on entry it latches `fading=true` (so later
   ticks can't restart the ramp) and linearly scales `player.volume` to 0 over `fadeSeconds`, in
   `FADE_STEP_MILLIS` (50ms) steps. Emits `TimerFadeStarted` — this is also what *opens* the
   re-arm window for `ShakeRearmDetector`.
3. While the fade window is open: a shake (`ShakeRearmDetector`, g-force > `SHAKE_G_FORCE_THRESHOLD`
   = 2.7g, debounced via `MIN_SHAKE_INTERVAL_MILLIS` = 1000ms) or a headphone-button press calls
   `mediaRepository.rearmTimer(trigger)`.
4. `rearmTimer` is the single gating point for **both** trigger sources — enabled flags
   (`rearmEnabled`, plus the per-trigger `rearmViaHeadphoneButton`/`rearmViaShake` toggle),
   `REARM_DEBOUNCE_MILLIS` (2000ms) since the last rearm, and whether remaining time is still
   inside the fade window all live here, not duplicated per trigger. A `DurationTimerOption`
   extends by `extensionSecondsFor(option, settings)` and reschedules; a `CurrentEpisodeTimerOption`
   instead sets `pendingChapterSkip` + sends `SuppressNextChapterStop` (consumed by `PlaybackTimer`
   so exactly the next chapter-boundary auto-transition doesn't expire the timer).
5. On expiry (`TimerExpired`), `SleepTimerFadeService` forces volume to exactly 0 even if the ramp
   lagged, and `PlaybackTimer.expire()` pauses the player *before* emitting the event, so auto-skip
   logic downstream sees the paused state at the right moment.

A **quick pause/resume** (< 2000ms — `MIN_PAUSE_FOR_RESET_MILLIS` in `PlaybackTimer` and
`REARM_DEBOUNCE_MILLIS` in `MediaRepository`; keep them equal) **re-arms** the timer via
`rearmTimer(RearmTrigger.PAUSE_RESUME)` (same gating as shake/button: fade window, `rearmEnabled`).
A **longer pause** (≥2s) on a duration timer **resets** it: the countdown is dropped and restarted at
`currentLimitMillis` (the full length). Caveat: a sub-2s rebuffer flicker also counts as a quick
pause/resume, so inside the fade window it can re-arm.

## `RearmExtensionMode` (how much a re-arm adds)

Read via `MediaRepository.extensionSecondsFor(option, settings)` — **this is the function the
auto-cache retention window's `SLEEP_TIMER_REARM_MULTIPLE` unit also calls** (see
[auto-cache.md](auto-cache.md)), so the two features can't drift out of sync:

- `FIXED` → extend by `settings.rearmExtensionSeconds` (default 300s, range 30-1800).
- `MATCH_TIMER_DURATION` → for a duration timer, extend by the timer's own original length
  (`option.duration * 60`); a `CurrentEpisodeTimerOption` has no fixed length, so this falls back
  to `rearmExtensionSeconds` too.

## Resume-rewind

A `TimerExpired`-caused pause rewinds a bit so you don't come back to audio you half-heard while
drifting off. The rewind is applied to the playhead **at the pause** (so the saved/synced position is
already the rewound one) in two steps, `MediaRepository.startTimerPauseRewind`:

1. At the pause: rewind by the short amount (`resumeRewindShortSeconds`).
2. Once the pause has lasted `resumeRewindThresholdSeconds` (default 120s): top up to the long total
   from `resumeRewindLongMode` — `FIXED` (`resumeRewindLongSeconds`), `MATCH_EXTENSION` (whatever
   `extensionSecondsFor` currently resolves to), or `MATCH_TIMER_DURATION` (full duration-timer length,
   or chapter start for an episode timer). The top-up is `long − short`; a long amount below the short
   one changes nothing.

Step 2 runs from a `MainThread.postDelayed` job, which dies with the process. If it never ran, the next
resume applies it instead when the pause outlasted the threshold (`resolveLongRewindOnResume`); a
resume inside the threshold cancels the job. Either path is `applyLongRewind`, which runs once.

If upstream's rewind-on-pause is enabled and the pause is not an episode-timer one, it has already
moved the playhead by its own amount; the short rewind only adds what is missing beyond it.

The pending top-up stays until the next play of **the same book** (`PendingLongRewind.bookId`).
`clearPreparedItem()` deliberately does *not* drop it — resuming hours later re-prepares the book
first, which used to silently cancel the rewind. A different book or `clearPlayingBook()` drops it.

**This interacts with auto-cache trim**: if the retention window were too aggressive, a rewind
could land on audio that trim already deleted. The auto-cache retention window exists
specifically to give this scenario a configurable safety margin — see auto-cache.md.

## Car-mode gating (driver distraction)

`CarConnectionMonitor.isConnected` is true for any Android Auto/Automotive connection.
`MediaRepository.updateTimer()` hard-gates on it:

```kotlin
val option = if (carConnectionMonitor.isConnected.value) null else timerOption
```

A collector on `carConnectionMonitor.isConnected` also calls `updateTimer(null)` the instant a car
connects mid-session — this **kills an already-running timer**, not just blocks future ones.
`rearmTimer` isn't car-gated directly, but since a connected car forces the timer option to null,
it returns immediately (no active timer to re-arm). **This gating is unconditional — there is no
user-facing toggle for it.** Don't add one without reconsidering the driver-distraction rationale.

## Settings reference (`SleepTimerSettings`, defaults in parens)

- Fade: `fadeEnabled`(false), `fadeSeconds`(30, 5-60), `chimeOnFadeStart`(true), `chimeFadeVolume`(50%)
- Re-arm: `rearmEnabled`(false), `chimeOnRearm`(true), `chimeRearmVolume`(50%),
  `rearmViaHeadphoneButton`(true), `rearmViaShake`(true), `rearmExtensionMode`, `rearmExtensionSeconds`(300, 30-1800)
- Resume rewind: `resumeRewindThresholdSeconds`(120, 0-900), `resumeRewindShortSeconds`(0, 0-300),
  `resumeRewindLongMode`, `resumeRewindLongSeconds`(0, 0-1800)
- Default-timer schedule: `defaultTimerScheduleEnabled`(false), start/end minute-of-day (default 20:00-08:00)

`SleepTimerSettingsScreen.kt` conditionally shows the extension/rewind seconds rows only when
their mode is `FIXED`, and disables re-arm sub-controls unless both fade and re-arm are enabled
(`rearmControlsEnabled = fadeEnabled && rearmEnabled`).
