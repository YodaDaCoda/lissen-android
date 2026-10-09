# First-launch permissions wizard

`OnboardingScreen` (`ROUTE_ONBOARDING`) is the start destination for a user with no credentials who has
not passed it yet; it asks for the two runtime permissions that used to ambush the user after login:

- **Notifications** (`POST_NOTIFICATIONS`, Android 13+) - the playback controls.
- **"Nearby devices"** (`ACCESS_LOCAL_NETWORK`, Android 17 / API 37) - not about SSO or Bluetooth: with
  `targetSdk = 37` the OS blocks all traffic to LAN addresses without it. A server on a public host
  does not need it.

Both can be skipped; "Continue" marks it done either way (`OnboardingPreferences`, a key outside
`SessionPreferences`/`PreferencesReset`, so a logout does not bring the wizard back).

- The wizard is skipped entirely (`isOnboardingRequired`) when the device needs neither permission or
  already holds both, and never shown to an install that is already logged in.
- **Fallbacks stay.** `LibraryScreen` still asks for both, but only while the wizard has not been
  completed (installs that predate it). `LoginScreen.withNetworkPermission` still asks before a server
  check or login if the local-network permission is missing - this is what covers "skipped it, then
  entered a LAN server".
- Fine location is not part of it: only the local-URL settings screen uses it (Wi-Fi name matching)
  and it is requested there.

Never run `connected*AndroidTest` against a phone that has the release `org.grakovne.lissen`: the
`debug` build type has no package suffix, so the test run replaces and then uninstalls it. Use an
emulator, or an install with a suffix (`dev`).
