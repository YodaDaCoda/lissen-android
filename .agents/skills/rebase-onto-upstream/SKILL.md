---
name: rebase-onto-upstream
description: Rebase this fork's slice patches onto a new upstream (GrakovNe/lissen-android) release. Use when the user asks to sync with upstream, rebase onto a new release or tag, or when the upstream_sync workflow failed on a conflict.
---

# Rebase slices onto a new upstream release

Read `.agents/README.md` ("Repository philosophy" and "Goal: upstreamable patches") first. The
fork is a stack of one-commit slices on upstream; the goal is to get them accepted upstream, so
the rebase must keep each slice clean, self-contained and extractable. Remotes: `origin` = fork,
`upstream` = GrakovNe.

Ask before anything outward-facing (push, force-push). Everything else below is local.

## 1. Preflight

```bash
git status --short            # tracked files must be clean; if not, do the work in a worktree (step 2)
git config log.showSignature false  # signed commits otherwise spam every `git log` with gpg lines; or pass --no-show-signature
git remote -v                 # origin = YodaDaCoda fork, upstream = GrakovNe
git fetch origin && git fetch upstream --tags
git rev-list --count HEAD..origin/main   # >0: the nightly sync already moved origin/main; reset to it first (ask)
```

Target = the tag the user named, else the newest release: `git tag --sort=-v:refname | grep -- '-release$' | head -1`.
Confirm the target with the user if it is not an explicit argument. (The nightly workflow rebases
onto `upstream/main`, not a release tag; if `main` is already on a newer upstream commit than the
target, say so and ask.)

```bash
OLD_BASE=$(git merge-base main upstream/main)   # last upstream commit our stack sits on
git log --oneline $OLD_BASE..main               # the slices; note which are fork-only
git log --oneline $OLD_BASE..$TARGET            # what upstream changed (skim for overlap)
```

## 2. Safety net and work branch

```bash
git branch backup/pre-$TARGET main              # local only, never pushed
git switch -c rebase/$TARGET main               # clean tree
# or, with uncommitted tracked changes you don't want to disturb:
git worktree add ../lissen-rebase -b rebase/$TARGET main && cd ../lissen-rebase
git config rerere.enabled true
```

(Uncommitted changes are not part of the rebase; commit them into the owning slice first if they
should be.)

## 3. Rebase

```bash
git rebase --onto $TARGET $OLD_BASE
```

Per conflict, resolve inside the slice being applied, then `git add` + `git rebase --continue`:

- Check the slice's doc in `.agents/` (`auto-cache.md`, `sleep-timer.md`, `onboarding.md`, …)
  for decisions the resolution must respect.
- **Upstream landed the same thing** (fully or partly): drop the slice (`git rebase --skip`) or
  shrink it to the remainder, and say so. This is the goal being reached; record it in step 6.
- **Upstream changed an API the slice uses**: adapt the slice in place. Never add a fix-up commit
  on top; every change lives in the slice that needs it.
- Prefer upstream's naming/structure over ours (see README).
- **Room**: upstream's schema version may have moved. Fork tables live in `ForkSchema` and must
  not take a Room version number; follow `.agents/room-migrations.md`.
- **Locales** (`strings.xml`): follow `.agents/localization.md`; watch for orphaned strings.
- Do not resolve by taking "ours" wholesale or by guessing. If intent is unclear, stop and ask.

## 4. Verify

```bash
git range-diff $OLD_BASE..backup/pre-$TARGET $TARGET..HEAD
```

Every slice should map 1:1 (`=` or small `!` diffs explained by upstream changes). Flag any
commit that appears/disappears unexpectedly, and any added "fix" commit.

Builds are only run when the user asks (see `.agents/build-system.md` "When to build"); offer, and
if yes run via `devenv shell --`, online:

```bash
devenv shell -- ./gradlew testDebugUnitTest
devenv shell -- ./gradlew assembleDev -Pminified=true -PsingleAbi=true
```

Optional bisectability check: `git rebase $TARGET -x 'devenv shell -- ./gradlew compileDebugKotlin'`.

## 5. Check upstreamability

Slices are stacked, so most of them legitimately depend on an earlier slice and will fail a
cherry-pick onto bare upstream. Test each slice **alone** and treat a failure as a finding to
explain, not an error:

```bash
git switch -c scratch/extract $TARGET
for c in $(git rev-list --reverse --no-show-signature $TARGET..rebase/$TARGET); do
  git cherry-pick --no-commit $c >/dev/null 2>&1 && r=ok || r=FAIL
  git cherry-pick --abort 2>/dev/null; git reset -q --hard $TARGET
  echo "$r $(git log -1 --format=%s $c)"
done
git switch rebase/$TARGET && git branch -D scratch/extract
```

Skip the fork-only slices. For each FAIL, find out which earlier slice or fork-only commit it
overlaps (`comm -12` of the two `git show --name-only` lists). Expected: it depends on an earlier
*feature* slice. Not expected: the **first** slice of a feature, or a slice that only touches
shared files with a fork-only commit. Report those; don't silently reorder history.

## 6. Land it

1. Update docs touched by the rebase: stale facts in `.agents/*.md`, slices dropped because
   upstream took them, the target release. Fold doc changes into the owning slice, not a new commit.
2. Show the user: new base tag, slice count before/after, dropped/shrunk slices, verification
   results, extraction failures.
3. On their go-ahead only: `git switch main && git reset --hard rebase/$TARGET`, then
   `git push --force-with-lease origin main`. Delete `rebase/$TARGET`; keep
   `backup/pre-$TARGET` until they say otherwise.

Never merge upstream into `main`. Never push the backup branch.
