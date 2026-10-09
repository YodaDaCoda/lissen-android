# Auto-cache for the currently-playing book

Upstream auto-caches ahead while playing, but only by chapter count, with no storage ceiling and
no clean-up — the cache only grows until manually cleared. This fork adds: a duration-based
lookahead option, a storage ceiling, and two safe clean-up mechanisms, scoped to the **single
currently-playing book** (not a fleet of several in-progress books).

Full original design doc (rationale, rejected alternatives, verification steps) is preserved at
`/home/will/.config/claude/plans/delegated-exploring-seahorse.md` on the machine this was built on
— this doc is the durable summary; that plan file is not guaranteed to survive across machines.

## Hard constraint: never auto-delete

The upstream maintainer has twice explicitly rejected any heuristic that infers a book is
"finished" and deletes it
([#245](https://github.com/GrakovNe/lissen-android/issues/245),
[#485](https://github.com/GrakovNe/lissen-android/issues/485)) — no "finished" signal is reliable
(seek-to-end, sleep timer expiry, and progress synced from another device all look identical to
genuinely finishing), and deletion is the one action offline users can't undo.

**Every clean-up path in this feature only acts on facts that have already, monotonically
happened** — never on an inferred signal:
1. **Trim already-played audio behind the playhead** — only removes audio whose chapter end is
   strictly ≤ current position, adjusted backward by a configurable retention buffer.
2. **Reclaim a book's auto-cached audio when a different book becomes current** — the trigger is
   "a new book replaced this one as the active book," an objective fact, not an inferred "finished."

**Never touches manually-downloaded content**, including a book that's *partially* auto-cached and
partially manual — this is the scenario the ownership table below exists to make provable, not
just hoped-for.

## Ownership tracking

`auto_cache_ownership` (chapter-level not book-level — book-level can't distinguish "fully
auto-owned, safe to whole-book-delete" from "partially manual, delete only the owned subset"):

```sql
CREATE TABLE auto_cache_ownership (
  bookId TEXT NOT NULL, bookChapterId TEXT NOT NULL, cachedAt INTEGER NOT NULL,
  PRIMARY KEY (bookId, bookChapterId),
  FOREIGN KEY (bookId) REFERENCES detailed_books(id) ON DELETE CASCADE
)
```

It is a **fork-owned table**: no Room entity, created and versioned by `ForkSchema`, and reached
through `AutoCacheOwnershipRepository` with plain SQL — see [room-migrations.md](room-migrations.md)
for why it stays out of Room. The cascade is what drops a book's ownership with the book.

- Marked owned (`ContentAutoCachingService.onCreate()`'s first `scope.launch`) when a
  dispatched auto-cache task completes (`ContentCachingProgress.statusFlow` → `CacheStatus.Completed`).
  The chapter IDs a dispatch will newly fetch are stashed in-memory (`pendingOwnership`, keyed by
  itemId) at dispatch time, then moved into the DB on completion, dropped on `CacheStatus.Error`.
- **Cleared the moment a user manually downloads the same chapter**
  (`CachingModelView.cache()` calls `autoCacheOwnershipRepository.clearOwned(bookId, targetChapterIds)`
  before dispatch) — a chapter the user explicitly asked for is never auto-cache's to reclaim again,
  whether or not auto-cache got there first. This is what makes the "manual downloads are safe"
  guarantee hold even in the partial-overlap case, not just the non-overlapping case.

## The two clean-up mechanisms (`ContentAutoCachingService.kt`)

Both slot into the existing reactive subscription
(`combine(playingBook, isPlaying, currentChapterIndex).distinctUntilChanged().collectLatest`) — no
new `RunningComponent`/WorkManager periodic job, this is event-driven only (confirmed with the
user as the preferred design).

**`reclaim(bookId)`** — called for the book being left behind the instant a different book becomes
current (`playingItem?.id != lastBookId`), before starting to manage the new one:
```kotlin
val owned = ownershipRepository.fetchOwnedChapterIds(bookId).toSet()
if (owned.isEmpty()) return
val cached = contentCachingManager.provideCachedChapterIds(bookId).first().toSet()
if (isFullyAutoOwned(owned, cached)) {
  contentCachingManager.dropCache(bookId)                 // whole-book delete: safe, nothing manual present
} else {
  // chapter-subset delete via dropConsumedChapters — survives a partially-manual book
}
ownershipRepository.clearAllOwned(bookId)
```
`isFullyAutoOwned(owned, cached) = owned == cached` is a top-level pure function specifically so
this branch decision has its own trivial unit test rather than being buried in integration-test-only
coverage.

**`trimConsumed(book)`** — called on every chapter-index/playback-state tick for the *same* book
still playing. Reads the configured `RetentionWindow` and computes chapters to trim via
`calculateChaptersToTrim` (pure function, `CalculateChaptersToTrim.kt`), then drops them via
`dropConsumedChapters` and clears their ownership rows.

Both reclaim and trim ultimately call the one new primitive,
**`ContentCachingManager.dropConsumedChapters(item, droppingChapters, keepingChapters)`**, which
uses `calculateFilesToDrop` (pure function, `CalculateFilesToDrop.kt`) to only delete a file if no
*kept* chapter still needs it — files can span more than one chapter
(`content/cache/common/FindRelatedFiles.kt` maps files↔chapters by time-range overlap), so a naive
per-chapter delete risks destroying audio a surviving neighbor relies on. This was verified against
the actual overlap logic, not assumed.

## Retention window (how far behind the playhead to keep)

`RetentionWindow(unit: RetentionUnit, amount: Int)`, default `MINUTES(5)`
(`domain/RetentionWindow.kt`), stored as Moshi JSON via `DownloadPreferences.getAutoCacheRetentionWindow()`.
Deliberately a free-form amount rather than a curated preset list (changed from an earlier
curated-list design after direct user feedback that it was too limiting) — the settings UI
(`RetentionWindowSettingsComposable.kt`) is a 3-unit slider picker, one `CommonSlider` per unit.

Three units, resolved in `calculateChaptersToTrim`:
- `CHAPTERS(n)` — keep the last `n` played chapters by index (chapter lengths vary too much for a
  duration-based "n chapters" to mean the same thing across books).
- `MINUTES(n)` — keep the last `n` minutes played (`chapter.end <= position - n*60`).
- `SLEEP_TIMER_REARM_MULTIPLE(n)` — keep `n ×` whatever the sleep timer's current re-arm extension
  resolves to (`MediaRepository.extensionSecondsFor`, same function the sleep timer itself uses —
  see [sleep-timer.md](sleep-timer.md)). This exists because the sleep timer's resume-rewind can
  seek backward past the playhead on resume; tying retention to the same function means the two
  features can't drift out of sync. Falls back to the raw `rearmExtensionSeconds` when no timer is
  currently active.

All three only ever look backward at what's monotonically already played — same never-infer
guarantee as the top-level constraint.

## Storage ceiling

`DownloadPreferences.getAutoDownloadStorageCeilingBytes()`, default **1 GB**
(`DEFAULT_STORAGE_CEILING_BYTES = 1_000_000_000L`). Checked in
`ContentAutoCachingService.exceedsStorageCeiling()` before extending the cached window: sums this
book's already-owned footprint + what the newly-requested chapters would add, against the ceiling.
If it doesn't fit, auto-cache **skips that cycle entirely** (not "cap the window at whatever fits"
— the implemented behavior is simpler than the original plan's "cap the window," confirm this is
still the desired behavior if storage pressure turns out to be common in practice). `BookFile.size`
is known upfront from server metadata, so no download is needed to estimate size.

UI: `StorageCeilingSettingsComposable.kt` — byte presets (500 MB/1/2/5 GB) via
`CommonSettingsItemComposable`, formatted with `android.text.format.Formatter.formatShortFileSize`
(the established byte-formatting convention — don't add a custom formatter).

## Duration-based lookahead (how far ahead to cache)

`DownloadOption.DurationDownloadOption(minutes: Int)`, peer to the existing
`NumberItemDownloadOption`/etc. `CalculateRequestedChapters.kt` resolves it as:
```kotlin
val cutoff = currentTotalPosition + option.minutes * 60.0
book.chapters.drop(chapterIndex.coerceAtLeast(0)).takeWhile { it.start < cutoff }
```
Displayed via `formatMinutesDuration()` (`ui/screens/common/DurationFormat.kt`) — "4h 10m" style,
not raw minutes; this helper is shared with the retention-window display too, added after direct
user feedback that "250 minutes" was unreadable.

## Storage visibility (what's using the ceiling)

Latest addition: a single `SELECT COALESCE(SUM(size), 0) FROM book_files` query
(`CachedBookDao.fetchTotalCacheSizeBytes()`), threaded through `CachedBookRepository` →
`ContentCachingManager.fetchTotalCacheSizeBytes()`, consumed by two ViewModels:
- `DownloadSettingsViewModel` → `CacheSettingsScreen`'s "Downloads" row now shows
  `"X of Y used"` (`R.string.download_settings_storage_used_of_ceiling`) against the storage
  ceiling, instead of a static "Manage saved content" hint.
- `CachingModelView` → `CachedItemsSettingsScreen` (the manage-saved-content list) shows a
  `"N items · X used"` header (`R.plurals.cached_items_total_summary`) plus each book's own size.

This is a **total cache size**, not scoped to auto-cache-owned bytes — it reflects manual +
auto-cached combined, matching what the ceiling itself is checked against per-book in
`exceedsStorageCeiling`. Refreshed on-demand (`refreshTotalCacheSize()`, called from
`LaunchedEffect(Unit)` and after any delete), not continuously observed — if items are added from
elsewhere while a settings screen is open, the total won't update until it's reopened.

## Known gaps

- No `ContentAutoCachingServiceTest.kt`-style integration test exists yet exercising the full
  reclaim/trim wiring end-to-end (real in-memory Room DB, fake flows) — the pure functions
  (`calculateChaptersToTrim`, `calculateFilesToDrop`, `isFullyAutoOwned`) are unit-tested, but the
  service-level wiring (calling the *right* drop primitive in the *right* branch) is not. Follow
  the style of `ContentCachingManagerTest.kt`/`ContentCachingIntegrationTest.kt` if adding one.
- The storage-ceiling-exceeded behavior ("skip this cycle" vs. "cap the window to what fits") may
  be worth revisiting if a low ceiling turns out to stall lookahead growth too often in practice.
