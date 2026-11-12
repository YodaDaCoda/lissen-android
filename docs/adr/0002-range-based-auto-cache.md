# Range-based auto-cache, separate from downloads

Auto-cache fetched whole files into the same store as manual downloads and checked a per-book storage ceiling against whole-file sizes. A book that is one large file (an `.m4b`) was larger than the 1 GB default ceiling, so every cycle was skipped and nothing was cached, with no visible reason.

Auto-cache is now a separate store of byte ranges, kept apart from downloads. A **Download** is manual, whole-file and fully offline. **Auto-cache** is automatic and exists so playback survives network drops.

- Auto-cache prefetches byte ranges into its own non-evicting Media3 `SimpleCache`, which playback already reads through. Time is converted to bytes as `file size × (time ÷ duration)` plus about 5% margin; streaming fills any shortfall.
- The ceiling applies to all auto-cached books together and never counts manual downloads. The lookahead is `min(chosen window, remaining ceiling)`; when full, prefetch stops.
- The retention window keeps its chapters / minutes / sleep-timer-multiple options. All reduce to "keep from time T onward" and trim byte ranges behind the playhead.
- Switching books deletes nothing. When the current book needs room, whole books are evicted oldest-first by last played on this device, never the current book. If it still does not fit, its window is cut to what fits.
- Book titles and `lastActiveAt` live in a fork-owned table in `ForkSchema`, so no Room migration is needed.
- Files already fully downloaded are skipped, and manual files are never counted or deleted.

## Consequences

- This replaces the "never auto-delete" rule in `.agents/auto-cache.md`. Evicting another book can delete lookahead audio the user has not played. That is accepted because the ceiling is user-set and eviction is driven by capacity and a local last-played fact, not by guessing that a book is finished. Upstream's objection (issues 245 and 485) was to inferring "finished".
- Auto-cached audio lives in the app's cache folder, so Android or the user can clear it. It no longer appears in "Manage saved content"; it has its own Auto-cache screen with per-book sizes, delete and "Clear all".
- On first launch after the change, files tracked in `auto_cache_ownership` are deleted once and the table is dropped.
