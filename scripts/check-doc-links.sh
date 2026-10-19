#!/usr/bin/env bash
# check-doc-links.sh - fail if any relative markdown link in the repo's docs
# does not resolve. Wired into hooks/pre-commit.
# Usage: scripts/check-doc-links.sh
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

bad=0
while IFS= read -r file; do
  dir=$(dirname "$file")
  while IFS= read -r target; do
    [ -z "$target" ] && continue
    case "$target" in
      http://*|https://*|mailto:*|'#'*) continue ;; # external or same-file anchor
    esac
    target="${target%%#*}" # strip fragment
    [ -z "$target" ] && continue
    if [ ! -e "$dir/$target" ]; then
      echo "BROKEN: $file -> $target"
      bad=$((bad + 1))
    fi
  done < <(
    grep -oE '\]\([^)]+\)' "$file" 2>/dev/null \
      | sed -E 's/^\]\(//; s/\)$//' \
      | sed -E 's/^<(.*)>$/\1/' \
      | sed -E 's/ "[^"]*$//'
  )
done < <(find . -name "*.md" -not -path "./.git/*" -not -path "./build/*" -not -path "./.gradle/*" | sort)

if [ "$bad" -gt 0 ]; then
  echo "FAIL: $bad broken doc link(s)"
  exit 1
fi
echo "OK: all relative doc links resolve"
