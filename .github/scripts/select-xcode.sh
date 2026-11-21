#!/usr/bin/env bash
#
# Selects the newest non-beta Xcode of the given major version installed on a
# GitHub macOS runner. Usage: select-xcode.sh 27
#
# Runner images install betas as e.g. Xcode_27.3_beta.app with an Xcode_27.3.app
# symlink, so a plain glob can land on a beta, and App Store Connect rejects
# uploads built with one. Symlinks are resolved before filtering.
set -euo pipefail

MAJOR="$1"

XCODE_APP="$(
  for app in /Applications/Xcode_"$MAJOR"*.app; do
    [ -e "$app" ] || continue
    real="$(cd "$app" && pwd -P)"
    case "$real" in *[Bb]eta*) continue ;; esac
    echo "$(defaults read "$real/Contents/Info" CFBundleShortVersionString) $real"
  done | sort -u -V | tail -1 | cut -d' ' -f2-
)"

if [ -z "$XCODE_APP" ]; then
  echo "No non-beta Xcode $MAJOR found. Installed:" >&2
  ls -d /Applications/Xcode*.app >&2
  exit 1
fi

echo "Using $XCODE_APP"
sudo xcode-select -s "$XCODE_APP/Contents/Developer"
xcodebuild -version
