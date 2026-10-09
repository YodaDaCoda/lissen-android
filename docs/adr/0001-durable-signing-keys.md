# Durable signing keys with fail-by-default builds

The `dev` and `minified` builds were signed with Gradle's auto-generated `debug.keystore`, so a regenerated keystore produced a different certificate and broke in-place updates on devices. We now sign `dev` and `release` with two separate, durable PKCS12 keys (`LISSEN_DEV_*` and `LISSEN_RELEASE_*` properties, stored under `${XDG_CONFIG_HOME:-$HOME/.config}/lissen/signing/` and backed up in Bitwarden). `release` is the Play upload key; the dev key is kept separate so a leaked sideload key never exposes it.

`assembleDev` and `assembleRelease` fail with a fix-it message when their key is not configured, so the certificate in use is never a surprise. `-PallowDebugSigning=true` falls back to the default debug keystore for CI and fresh clones, which do not hold the keys. The `minified` build type (package `.minified`) is removed; minification is the orthogonal `-Pminified` flag on `dev` and `release`.

## Consequences

- Devices with APKs signed by an old throwaway key must uninstall once.
- Losing the dev key costs a reinstall; losing the release key is recoverable only via Play's upload-key reset.
