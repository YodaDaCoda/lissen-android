# Auto-cache for the currently-playing book

Auto-cache keeps the next stretch of the playing book so playback rides out network drops. It is
**separate from downloads** (manual, whole files, fully offline): its own store, its own ceiling, and
neither ever counts or deletes the other. Decision and trade-offs: `docs/adr/0002-range-based-auto-cache.md`;
terms (Download, Auto-cache, Lookahead, Retention window, Ceiling, Eviction): `GLOSSARY.md`.

## Where the logic lives

- **`PlanAutoCache.kt`** is the one pure function, `planAutoCache(input) -> prefetch / trim / evictBookIds`.
  No I/O, no Android: all the rules are unit-tested in `PlanAutoCacheTest`. Add rules here, not in the service.
  - Lookahead window (duration, chapter count, current, remaining, all) is a span of book time from the
    playhead, mapped to bytes as `size × (time ÷ duration)` + 5% margin at the window's end. Approximate
    on purpose: streaming fills any shortfall. No stored bitrate.
  - The ceiling is a cap: the window is cut to what fits, never skipped. It counts all auto-cached books
    together, never downloads. Fully downloaded files are skipped.
  - Retention (chapters / minutes / sleep-timer multiple) all reduce to one cutoff time; cached bytes
    before it are trimmed, and the lookahead never starts before it, so trimmed audio is not refetched.
  - Eviction: only when something must be fetched, whole books, oldest `lastActiveAt` first, as few as
    needed, never the playing book. If that still is not enough, the window is cut.
- **`AutoCacheStorage.kt`** is the thin Media3 adapter: ranges in, ranges out. Not unit-tested; check on a
  device. Media3 stores spans of a few MB, so a trim can leave a sliver.
- **`ContentAutoCachingService.kt`** decides when to run (eligibility, network, library type, delay) and
  applies the plan. Every early exit logs its reason (`Auto-cache skipped: ...`); these only reach logcat on
  a debuggable build, otherwise turn on activity logging.
- **`AutoCacheBookRepository`** / `auto_cache_books` (fork-owned, see `ForkSchema`, no Room migration):
  title and `lastActiveAt`, set while a book plays on this device.
- **`AutoCachePause`**: clearing the playing book from settings holds auto-cache off for it until the next
  play start, so the delete is not undone by an immediate refill.

## Storage and keys

The auto-cache is its own `SimpleCache` (`MediaModule.provideAutoCache`, `NoOpCacheEvictor`, in the app's
external cache folder, so Android or the user can clear it). It never evicts by itself: prefetched audio
ahead of the playhead has not been read and would look least recently used. Playback reads it ahead of
the streaming cache and never writes it (`LissenDataSourceFactory`). Bytes are keyed `lissen:<book>:<file>`
(`toCacheKey`), not by the server URL, so changing host does not orphan them.

## Deletion rules

Auto-cache may delete only (a) audio behind the playhead beyond the retention window, and (b) another
book's whole auto-cached audio when the playing book needs the space. Switching books deletes nothing.
Downloads are never touched. This replaced an earlier "never auto-delete" rule; see the ADR.

## Legacy

Before this, auto-cache saved whole files beside downloads and tracked them in `auto_cache_ownership`.
`LegacyAutoCacheCleanup` removes those once at launch (a manual download is never listed) and drops the
table.

## Known gaps

- The Media3 adapter and the service wiring have no automated test; verify on a device.
- Time-to-byte mapping is approximate (VBR, container overhead); fine for prefetch, not for exactness.
