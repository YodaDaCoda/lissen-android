# Android Auto

## Status

Android Auto support was already substantially implemented upstream before this fork touched it —
browse tree, search, custom transport buttons via Media3's `MediaLibrarySessionCallback`. The work
done here was **hardening/polish**, not building it from scratch:

- **Sleep timer disabled while driving** — see [sleep-timer.md](sleep-timer.md)'s "Car-mode gating"
  section. `CarConnectionMonitor` (`playback/CarConnectionMonitor.kt`) wraps
  `androidx.car.app.connection.CarConnection`; `MediaRepository.updateTimer()` hard-gates on it,
  unconditionally, with no user-facing toggle. Driver-distraction features should default to this
  same unconditional-gate pattern rather than adding a settings toggle a distracted driver would
  need to have set in advance.
- `gradle/libs.versions.toml`: `androidx-car-app = "1.7.0"` added as a dependency for
  `CarConnection`.

## The "app doesn't show up in the car" gotcha

Not a bug — Android Auto has a developer-mode gate for sideloaded (non-Play-Store) apps that's
easy to miss: **Settings → Apps & notifications → See all apps → Android Auto → Advanced →
Additional settings in the app → About → tap Version 10x → enable "Unknown sources"**. Both the
`release` and `dev` build types are sideloaded here (no Play Store listing), so this applies to
every install on this fork, not just test builds. If a future install mysteriously doesn't appear
in the car's app list, check this toggle before assuming a code regression.
