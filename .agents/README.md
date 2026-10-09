# Fork notes

## Repository philosophy (project foundation — read first)

This repo is **a series of clean slice patches applied on top of upstream**
(`GrakovNe/lissen-android`):

- Each slice implements its functionality **in full, as one commit, directly on top of upstream
  `main`** (plus earlier slices it depends on). A slice = one feature/fix, reviewable and
  droppable on its own.
- **No merge commits from upstream.** Sync by rebasing the slices onto the new upstream `main`.
- **No stacking fix-up commits.** A follow-up fix to a slice is folded into that slice
  (`git commit --fixup` + `git rebase -i --autosquash`, or amend), not appended.
- Rewriting history to reach this state is acceptable (force-push with lease). Capturing this rule
  matters more than immediately cleaning the existing history.
- `.github/workflows/upstream_sync.yml` does the rebase + dev release on a schedule; it fails
  loudly on conflicts rather than guessing — resolve the slice by hand.

This is a personal fork of [GrakovNe/lissen-android](https://github.com/GrakovNe/lissen-android),
tracked directly on `main` against upstream `origin` (no separate fork remote). Fork-specific work
lives as a long-running uncommitted/locally-committed diff on top of upstream `main` — check
`git status` and `git log` before assuming what's shipped vs. in progress.

Each doc below covers one feature area: what it does, the non-obvious decisions behind it, and
anything a future change needs to respect. Read the relevant doc before touching that area —
these capture reasoning that isn't visible from the code alone (rejected alternatives, upstream
constraints, bugs already hit once).

- [build-system.md](build-system.md) — `release`/`dev` build types, `-Pminified`/`-PsingleAbi` flags, per-build icon ribbons, devenv/Nix toolchain.
- [auto-cache.md](auto-cache.md) — time-windowed auto-cache for the currently-playing book: storage ceiling, retention window, ownership tracking, the "never auto-delete" constraint it was built around.
- [sleep-timer.md](sleep-timer.md) — sleep timer subsystem: car-mode disable, shake-to-rearm, chime/fade, rearm extension modes.
- [android-auto.md](android-auto.md) — Android Auto status and the sideload "Unknown sources" gotcha.
- [room-migrations.md](room-migrations.md) — migration discipline, and the launch-crash postmortem that established it.
- [onboarding.md](onboarding.md) — the first-launch permissions wizard, which prompts are where and why.
- [localization.md](localization.md) — locale file footprint and the orphaned-string trap.

## Conventions this fork follows

- No `scripts/` directory exists yet; use `./gradlew` tasks directly, **via `devenv shell --`, online** (see build-system.md "How to run things") — don't
  invent wrapper scripts unless asked to standardize.
- New preferences go through `SecurePreferenceStore`; Moshi JSON for structured values (see the
  `getInt`/`getLong`/`getString` + JSON-blob pattern in `DownloadPreferences.kt`).
- Non-trivial branching logic is extracted as a free function in its own file
  (`CalculateRequestedChapters.kt`, `CalculateFilesToDrop.kt`, `CalculateChaptersToTrim.kt`,
  `isFullyAutoOwned`) so it's unit-testable without Room/Android mocking — follow this pattern
  rather than burying logic inside a service/ViewModel.
- Room migrations are hand-written raw SQL that must match the `@Entity` annotation exactly,
  verified against the exported schema JSON — see room-migrations.md, this has bitten us once in
  production already. Tables this fork adds do **not** go through Room's version/migrations (that
  counter is upstream's): they live in `ForkSchema`, same file.
