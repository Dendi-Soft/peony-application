# Issue Tracker

Issues live on GitHub at https://github.com/Dendi-Soft/peony-application/issues. Use the `gh` CLI to interact with them.

## Creating Issues

```bash
# Create a new issue
gh issue create --title "Issue title" --body "Issue description"

# Attach a PR as an issue (if needed)
gh pr create --title "PR title" --base develop --draft=false
```

## Closing issues

Feature pull requests merge into `develop`, but the repository's default branch is `main`.
GitHub only applies `Closes #N` / `Fixes #N` keywords when a PR merges into the default
branch, so a `develop` merge leaves the linked issues open.

After merging a feature PR into `develop`, close each implemented issue manually with a
pointer to the PR:

```bash
gh issue close N --reason completed --comment "Implemented by #<pr>, merged into develop."
```

## Dependencies (blocked-by)

- `gh issue edit N --add-blocked-by M` marks **N as blocked by M**
- `gh issue edit N --add-blocking M` marks **N as blocking M** (the reverse direction)
- Verify with `gh issue view N --json blockedBy,blocking`
