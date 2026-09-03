#!/usr/bin/env bash
# Commits the package.json / yarn.lock the release build rewrote in the current module
# (see sync-version.js), and only when they changed: maven-scm-plugin's checkin fails on
# a module that has nothing to commit, which is the case as soon as a package.json
# already carries the release version (#155). Invoked as exec:exec@commit-synced-files
# from the release-prepare profile, once per reactor module, with the module directory
# as working directory. The [skip ci] marker keeps the CI workflow from triggering
# mid-release, like the release plugin's own commits.
set -euo pipefail

version="${1:?usage: commit-synced-files.sh <maven-version>}"

files=()
for candidate in package.json yarn.lock; do
    [ -f "$candidate" ] && files+=("$candidate")
done
if [ ${#files[@]} -eq 0 ] || git diff --quiet -- "${files[@]}"; then
    echo "No synced file to commit in $(pwd)"
    exit 0
fi

git commit --quiet -m "chore(release): Sync package version to ${version} [skip ci]" -- "${files[@]}"
echo "Committed ${files[*]} in $(pwd)"
