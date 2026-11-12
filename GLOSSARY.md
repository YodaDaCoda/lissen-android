# Lissen

An audiobook player. This glossary covers the sleep timer and its automatic arming, and auto-cache versus downloads.

## Sleep timer

**Default timer**:
The sleep timer option the user has chosen to be applied automatically, without setting it by hand.
_Avoid_: Auto timer, preset timer

**Schedule window**:
The daily start–end time range in which the default timer may be armed; it can wrap past midnight (e.g. 20:00–08:00).
_Avoid_: Time window, quiet hours

**Arming**:
Applying the default timer at the moment playback starts, when no timer is active and the time falls within the schedule window.
_Avoid_: Triggering, kicking in

**Session**:
The span from a book being prepared, or a timer expiring, until the next of those.
_Avoid_: Playback session (ambiguous with a single play/pause run)

**Manual override**:
The user setting or cancelling the timer by hand, which suppresses arming for the rest of the session.
_Avoid_: Manual timer

## Storage

**Download**:
Audio the user saved by hand as whole files, for fully offline listening. Never counted against the auto-cache ceiling or deleted by auto-cache.
_Avoid_: Saved content (for auto-cache), offline copy

**Auto-cache**:
Automatically prefetched byte ranges of the playing book, kept so playback survives network drops. Separate from downloads.
_Avoid_: Auto-download, automatic download

**Lookahead**:
How far ahead of the playhead auto-cache prefetches, capped by what remains under the ceiling.
_Avoid_: Download window

**Retention window**:
How much already-played audio auto-cache keeps behind the playhead; older ranges are trimmed.
_Avoid_: Buffer behind

**Ceiling**:
The size limit on all auto-cached books together. A cap that shortens the lookahead, never a switch that stops caching.
_Avoid_: Storage limit (ambiguous with downloads)

**Eviction**:
Deleting a whole auto-cached book, least recently played first, so the current book fits under the ceiling.
_Avoid_: Reclaim, clean-up
