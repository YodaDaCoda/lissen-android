# Room migration discipline

## Fork tables stay out of Room

Room's schema version (`LocalCacheStorage` `version = N`, `MIGRATION_x_y`, the `schemas/*.json`
export) is **upstream's counter**. This fork must not take a number from it: a fork migration 24→25
and upstream's own 24→25 (the genres table) cannot both apply to one database, and every sync would
renumber one of them. Fork tables are therefore created by `ForkSchema` (content/cache/persistent),
versioned in their own `fork_schema_version` table and run from the database's `onOpen`:

- Append a step to `ForkSchema.steps`; never edit or reorder an applied one. Steps are idempotent
  (`IF NOT EXISTS`).
- No `@Entity`, so no DAO and no schema JSON: use plain SQL (see `AutoCacheOwnershipRepository`).
  Room ignores tables it has no entity for.
- A foreign key into upstream's tables is fine and gives the cascade. Never rebuild
  (`DROP`/rename) a parent table from a fork step.
- Test with an instrumented DB test (`AutoCacheOwnershipRepositoryTest`).

`LocalCacheStorage.kt`, `Migrations.kt` and `schemas/` must stay byte-identical to upstream.

**Stale dev installs.** A `.dev` install that ran a build from before this scheme holds a Room
database at upstream's version number with a different identity hash (or an extra table at a number
upstream later used); it crashes on open. There is no migration for that: uninstall the dev app or
clear its data once.

## The rule (upstream's Room tables)

A hand-written `Migration(from, to)`'s raw `CREATE TABLE` SQL **must exactly match** what Room
derives from the corresponding `@Entity` annotation — including every `FOREIGN KEY`/`PRIMARY KEY`
clause. Room validates the live schema against the entity-derived schema on database open; any
mismatch throws at that point, not at compile time.

**Before adding or editing a migration:**
1. Write the raw SQL to match the `@Entity` annotation's `foreignKeys`/`primaryKeys`/`indices`
   field-for-field — don't paraphrase or simplify.
2. After running `./gradlew` (which regenerates the exported schema via
   `ksp { arg("room.schemaLocation", ...) }`), diff the migration's SQL against the actual exported
   schema JSON at `app/schemas/org.grakovne.lissen.content.cache.persistent.LocalCacheStorage/<version>.json`
   — its `createSql` field is the ground truth, not a guess from reading the entity.
3. Add a `MigrationTestHelper`-based instrumented test in
   `app/src/androidTest/.../LocalCacheStorageMigrationTest.kt` that calls
   `runMigrationsAndValidate(..., validateDroppedTables = true, ...)` — this is what actually
   catches a schema mismatch; a plain unit test won't, since Room's `MigrationTestHelper` is the
   only thing that exercises the real schema-validation-on-open path outside a real device.

## Why this is a hard rule, not a style preference

This exact mistake crashed the app on launch for a real user in production, once. A migration
that added the fork's `auto_cache_ownership` table (see [auto-cache.md](auto-cache.md)) initially omitted
the `FOREIGN KEY (bookId) REFERENCES detailed_books(id) ON DELETE CASCADE` clause that the entity's
`@Entity(foreignKeys = [...])` declared. Every **fresh install** worked fine, because Room generates
the schema directly from the entity on first open, bypassing migrations entirely — so this class of
bug is invisible in that path and only shows up for an **upgrading existing install**, which is
exactly the case a developer rebuilding-and-reinstalling onto a clean emulator won't catch.

Found by direct comparison against other FK-bearing tables' migrations in the same file
(`MIGRATION_7_8`/`MIGRATION_8_9`'s `book_series` table), then confirmed by reading the actual
exported schema JSON. Fixed by matching the migration SQL to the JSON's `createSql` exactly, plus
adding a `LocalCacheStorageMigrationTest.kt` test (inserts a book, runs+validates the migration,
inserts an ownership row, deletes the book, confirms cascade-delete empties the ownership table) —
this test would have caught the bug immediately, had it existed first. That table has since moved to
`ForkSchema` and `AutoCacheOwnershipRepositoryTest` covers the same cascade.

## ACRA crash reporting — who sees what

Upstream initialises ACRA (`acra.core`/`acra.http`/`acra.toast`) in release builds and posts crash
reports to the **upstream maintainer's** server, with credentials in `LissenApplication`. This fork
does not initialise it, so nothing is sent anywhere; the "crash reporting" switch in the diagnostics
settings does nothing here, and `ACRA.errorReporter` calls are no-ops. For debugging a crash in this
fork use `logcat` (or the test above). A replacement needs a collector of our own and an `initAcra { httpSender { ... } }` block in
`LissenApplication.attachBaseContext` (see upstream's for the shape).
