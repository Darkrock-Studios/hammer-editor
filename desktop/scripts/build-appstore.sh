#!/usr/bin/env bash
#
# Build a sandboxed, signed .pkg ready to upload to the Mac App Store
# (via Transporter, Fastlane pilot, or App Store Connect upload).
#
# Usage: ./desktop/scripts/build-appstore.sh [BUILD_NUMBER]
#
# Requirements:
#   - Mac App Distribution + Mac Installer Distribution certs installed in
#     the login keychain (see plan Phase 2a).
#   - desktop/embedded.provisionprofile and desktop/runtime.provisionprofile
#     in place (see plan Phase 2c). Both are gitignored.
#   - desktop/resources/macos/libjnidispatch.jnilib pre-extracted from JNA
#     5.18.1 (see plan Phase 1e).
#
# Important: builds signed with "3rd Party Mac Developer" certificates only
# run when installed via TestFlight or the App Store — you cannot launch the
# .pkg locally to test. Use Transporter to upload, then install via TestFlight.

set -euo pipefail

BUILD_NUMBER="${1:-1}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

# Sanity-check the prerequisites that, if missing, cause cryptic failures
# 10 minutes into the build. (Native libs are extracted at build time by
# :desktop:extractMacosNativeLibs into build/macos-native-libs/.)
for f in \
	desktop/embedded.provisionprofile \
	desktop/runtime.provisionprofile \
	desktop/entitlements.plist \
	desktop/runtime-entitlements.plist
do
	if [[ ! -e "$f" ]]; then
		echo "ERROR: required file missing: $f" >&2
		echo "       See plan phases 1e / 2c / 3a." >&2
		exit 1
	fi
done

if ! security find-identity -v -p codesigning | grep -q "3rd Party Mac Developer Application"; then
	echo "ERROR: Mac App Distribution certificate not found in keychain." >&2
	echo "       security find-identity -v -p codesigning" >&2
	exit 1
fi

echo "→ Building Hammer for the Mac App Store (build #$BUILD_NUMBER)"

./gradlew --stop
./gradlew :desktop:packageReleasePkg \
	-PmacOsAppStoreRelease=true \
	-PbuildNumber="$BUILD_NUMBER" \
	--no-daemon

APP_PATH="$(find desktop/build/installers/main-release/app -maxdepth 1 -name '*.app' -print -quit)"
PKG_PATH="$(find desktop/build/installers/main-release/pkg -name '*.pkg' -print -quit)"

if [[ -z "$APP_PATH" ]] || [[ -z "$PKG_PATH" ]]; then
	echo "ERROR: build produced no .app or .pkg" >&2
	exit 1
fi

echo
echo "→ Verifying signatures"
codesign --verify --deep --strict --verbose=2 "$APP_PATH"
pkgutil --check-signature "$PKG_PATH"

echo
echo "✓ Done."
echo "  .app: $APP_PATH"
echo "  .pkg: $PKG_PATH"
echo
echo "Next: open Transporter, drag the .pkg in, click Deliver."
