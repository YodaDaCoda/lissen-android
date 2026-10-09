# Localization

## The locale footprint is bigger than it looks

This project has **~27 `values*` locale directories** (`values`, `values-ar`, `values-az`,
`values-cs`, `values-da`, `values-de`, `values-es`, `values-fi`, `values-fr`, `values-hr`,
`values-hu`, `values-in`, `values-it`, `values-ka`, `values-ko`, `values-nl`, `values-nn`,
`values-pl`, `values-pt`, `values-pt-rBR`, `values-ru`, `values-sl`, `values-ta`, `values-zh-rCN`,
...), not just `values`/`values-ru` as it's easy to assume from casually browsing the tree.
Translations are crowd-sourced via Weblate (see the `Translated using Weblate (...)` commits in
`git log`).

## Adding a string: safe

A new string added only to `values/strings.xml` and not to the other ~26 locale files is
**non-breaking** — Android falls back to the default (`values`) resource when a locale-specific
one is missing. Don't feel obligated to backfill every locale yourself when adding a string; that's
what Weblate is for, and manually translating into languages like Korean, Arabic, or Chinese
without native fluency risks worse-than-nothing translations.

## Removing/renaming a string: NOT safe without cleanup

**Deleting or renaming a string key from `values/strings.xml` while another locale file still has
its own translation of that key orphans it there** — AAPT2 emits a build warning
(`removing resource ... <key> without required default value`) and it's easy to miss if you only
check `values/strings.xml` for leftover references.

**Checklist when removing/renaming any string:**
```bash
grep -rl '"<old_key_name>"' app/src/main/res/values*/strings.xml
```
Every hit that isn't `values/strings.xml` itself needs the same key removed (or renamed in sync).
A clean full rebuild after the change should show **zero** AAPT2 resource warnings — treat any such
warning as a signal that a locale file was missed, not as noise to ignore.

This already happened once in this fork: replacing `settings_screen_cached_items_hint` with a
dynamic storage-summary string (see [auto-cache.md](auto-cache.md)) orphaned the old key in 22
other locale files; fixed by grepping for it and stripping the dead line from each.

## Current gap

None of the strings added in this fork's auto-cache/sleep-timer/storage-visibility work have been
backfilled into the ~23 non-English, non-Russian locales — they currently fall back to English.
Functionally fine, just incomplete coverage; worth mentioning if the user asks about translation
status, not something to proactively "fix" given the scale and the risk of a bad machine
translation being worse than the English fallback.
