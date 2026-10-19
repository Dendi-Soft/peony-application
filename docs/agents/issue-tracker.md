# Issue Tracker

Issues live on GitHub at https://github.com/Dendi-Soft/peony-application/issues. Use the `gh` CLI to interact with them.

## Creating Issues

```bash
# Create a new issue
gh issue create --title "Issue title" --body "Issue description"

# Attach a PR as an issue (if needed)
gh pr create --title "PR title" --base main --draft=false
```

## Dependencies (blocked-by)

- `gh issue edit N --add-blocked-by M` marks **N as blocked by M**
- `gh issue edit N --add-blocking M` marks **N as blocking M** (the reverse direction)
- Verify with `gh issue view N --json blockedBy,blocking`
