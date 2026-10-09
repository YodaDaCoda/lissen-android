# Build system

## Build types

Only two distributable build types exist: `release` and `dev`. There is deliberately no
`release-mini`/`dev-mini` pair — minification is a flag, not a build type, so the matrix of
(package identity × minified × ABI) doesn't explode into one Gradle build type per combination.

| Build type | Package id suffix | Signing | Minify default | Debuggable default | Purpose |
|---|---|---|---|---|---|
| `release` | (none) | release keystore if `RELEASE_STORE_FILE` is set | `true` | `false` | Play Store / real installs |
| `dev` | `.dev` | debug keystore | `false` | `true` | Sideload builds for manual testing, installs alongside a real install |
| `minified` | `.minified` | debug keystore | always `true` | always `false` | CI/E2E only (`:minifiedTest` targets it by name) — **ignores `-Pminified`**, don't touch |
| `debug` | (AGP default) | debug keystore | n/a | n/a | Exists only as the `./gradlew testDebugUnitTest` compile target — not meant to be installed, use `dev` for that |

Source: `app/build.gradle.kts:140-185`.

## Composable flags

Two Gradle properties layer on top of `release`/`dev`, read once via `-P` flags at the top of
`app/build.gradle.kts`:

- **`-Pminified=true|false`** — overrides the build type's minify default (`isMinifyEnabled`,
  `isShrinkResources`, `isDebuggable` all move together). Independent of which build type you
  build.
- **`-PsingleAbi=true`** (+ optional **`-PtargetAbi=<abi>`**, default `arm64-v8a`) — strips every
  native-lib ABI except the target one, applied only to `release`/`dev` via the `androidComponents`
  variant API (never `minified`). A plain `assembleDev`/`assembleRelease` with no flag still ships
  all four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).

Example — a small sideload build to test on a Pixel (arm64-v8a only):

```bash
./gradlew assembleDev -Pminified=true -PsingleAbi=true
```

Both flags need an explicit `=true`: a bare `-Pminified` or `-PsingleAbi` is not the same thing
(`-Pminified` alone fails the build, and a bare `-PsingleAbi` still builds every ABI).

This produces a ~11 MB APK at `app/build/outputs/apk/dev/app-dev.apk`, vs. ~100 MB unminified/all-ABI.

**Why a flag and not more build types:** a `-Pminified` + `-PsingleAbi` combination for `dev` alone
would need 4 build types (`dev`, `dev-mini`, `dev-arm64`, `dev-mini-arm64`) to cover, and that's
before `release` gets the same treatment. Flags compose; build types don't.

## Per-build launcher icon

EasyLauncher (`app/build.gradle.kts:14-46`) overlays a colored ribbon + label onto the launcher
icon per build type, so a sideloaded `.dev` build is visually distinct from a real install on the
same device's home screen:

- `debug` → orange `#FF6F3F` "DEBUG" ribbon
- `dev` → blue `#1565C0` "DEV" ribbon
- `release`/`minified` → unmodified icon

Applied via `apply(plugin = "com.starter.easylauncher")` + the typed `EasyLauncherExtension`
rather than the plugins DSL, specifically so it shares a classloader with the webp-imageio reader
declared in the root buildscript (needed to read the `.webp` launcher icons from the command
line). If EasyLauncher ever needs touching, keep it off the plugins DSL for this reason.

## devenv / Nix toolchain

`devenv.nix` (fork-only; the build-system slice) pins the Android SDK components to what this
project needs:

- `compileSdk`/`targetSdk` 37, via `android.platforms.version = [ "37.0" "36" ]` — AGP resolves
  `compileSdk = 37` to the SDK component `platforms;android-37.0` specifically; `"37.1"` was tried
  and AGP refused to treat it as equivalent, then tried to auto-install `37.0` itself, which fails
  on the Nix store's read-only filesystem.
- JDK 25 (`pkgs.jdk25`), matching `compileOptions`/`java.toolchain` in `app/build.gradle.kts`
  (language level 25).
- No emulator/device images (`android.emulator.enable = false`, etc.) — this devenv only needs to
  compile and run unit tests, not launch an emulator.

Run builds through `devenv shell -- ./gradlew ...` (or inside `devenv shell`) rather than a bare
system Gradle, so the pinned SDK/JDK are on `PATH`.

## How to run things (pitfalls already hit)

Always go through devenv, and never pass `--offline`:

```bash
devenv shell -- ./gradlew testDebugUnitTest -q
devenv shell -- ./gradlew assembleDev -Pminified=true -PsingleAbi=true -q
```

- **Bare `./gradlew` fails with `error: invalid source release: 25`.** The login shell's JDK is 21;
  only the devenv shell has JDK 25. Don't "fix" it by lowering `compileOptions`.
- **`--offline` fails with `No cached version of com.android.tools.build:aapt2 ... available`.**
  The aapt2 artifact isn't in the local Gradle cache until an online build has fetched it. Run
  online; Gradle caches afterwards.
- **A full unit-test run takes ~2 min+ and a cold build longer.** Tools with a 2-minute foreground
  timeout (including agent shells) must run these in the background, not foreground.
- Output APK: `app/build/outputs/apk/dev/app-dev.apk` (~11 MB with the flags above; installs
  alongside a real install as `.dev`).
- Unit tests (`testDebugUnitTest`) don't need a device; `androidTest` does and is not run by CI
  here.
- To verify a single slice in isolation before committing:
  `git stash push --keep-index -u`, run the commands above, commit, then `git stash pop`
  (resolve any conflict on files that were split by hunk, e.g. `strings.xml`).
