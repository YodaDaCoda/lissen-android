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
- An x86_64-only emulator and system images (`android.abis = [ "x86_64" ]`, `android.emulator.enable`,
  `android.systemImages.enable`) so a build can be run and checked without a device; API 36 and
  API 37.0 `google_apis_playstore` images are provided. arm64 images are left out (slow, several GB).

Run builds through `devenv shell -- ./gradlew ...` (or inside `devenv shell`) rather than a bare
system Gradle, so the pinned SDK/JDK are on `PATH`.

## Running on the emulator

Create the AVD outside the repo and boot it headless (needs `/dev/kvm`):

```bash
export ANDROID_AVD_HOME=/path/outside/repo/avd ANDROID_USER_HOME=/path/outside/repo/androiduser
devenv shell -- bash -c 'avdmanager create avd -n lissen36 -k "system-images;android-36;google_apis_playstore;x86_64" -d pixel_6'
devenv shell -- emulator -avd lissen36 -no-window -no-audio -no-snapshot -gpu swangle_indirect -cores 4 -memory 3072
adb install -r app/build/outputs/apk/dev/app-dev.apk   # installs as org.grakovne.lissen.dev
adb shell am start -n org.grakovne.lissen.dev/org.grakovne.lissen.ui.activity.AppActivity
```

- **Use the API 36 image, not 37.0.** On the 37.0 preview image `surfaceflinger` aborts
  (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`) in every `-gpu` mode, which
  restarts the guest's system server over and over; `screencap` hits the same assertion. API 36 is
  stable. Consequence: the API-37-only local-network prompt can't be exercised here.
- Wait for `adb shell pm path android` to succeed, not just `sys.boot_completed`: system services
  start later, and `adb install` fails with "Can't find service: package" until then.
- `-PsingleAbi=true` keeps arm64-v8a by default; the x86_64 image also runs arm64 (translated), or
  pass `-PtargetAbi=x86_64` for a native build.
- The guest restarts if the host is starved (other Gradle/Kotlin daemons); check `uptime`/`free`.
- No server is needed to see the wizard, login, and Preferences (reachable from the login
  screen's gear). For playback, Auto-cache and the player, run the mock server
  (`tools/mock-abs/server.py`, a hand-written stand-in for Audiobookshelf, not the real thing),
  then log in on the emulator with server `http://10.0.2.2:13378`, user `demo`, any password.
  It serves "The Test Book" (3 tracks, 5 chapters, about 12 min, 40% played) and "A Short Story"
  (one 90 s track) as generated WAV tones with range requests. It implements only what Lissen
  calls, so a pass proves nothing about real Audiobookshelf quirks, formats or file sizes.
- `adb exec-out screencap -p > shot.png` for screenshots; `uiautomator dump` for UI text.

## When to build (hard rule)

**Only run a Gradle build/test when the user explicitly asks for one.** Don't build "to verify" an
edit, and don't leave anything running afterwards. The machine is memory-constrained (14 GiB RAM,
swap routinely near full); a lingering Gradle + Kotlin daemon pair cost ~5.6 GB of swap.

- `gradle.properties` sets `org.gradle.daemon=false` and `kotlin.compiler.execution.strategy=in-process`,
  so each build is a single JVM that exits when done. Slower cold start is an accepted trade-off.
  Don't re-enable the daemon or pass `--daemon`.
- After any build, confirm nothing survived: `pgrep -fa 'gradle|kotlin-build-tools'` should be empty;
  if not, `devenv shell -- ./gradlew --stop`, then `pkill -f 'GradleDaemon|kotlin-build-tools'`.

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
