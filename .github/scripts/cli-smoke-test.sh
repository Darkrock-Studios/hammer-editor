#!/usr/bin/env bash
# Runs a packaged `hammer` CLI as a headless machine would: no X, no Wayland, fresh data.
# Usage: cli-smoke-test.sh <hammer launcher> <webp image>
#
# The WebP is exported inside a DOCX, which re-encodes it as PNG through Skia. Skia failures are
# swallowed there (the image falls back to its name), so the check is that the PNG is in the file.
set -euo pipefail

hammer="$1"
webp="$2"

home="$(mktemp -d)"
trap 'rm -rf "$home"' EXIT

# Java reads user.home from the passwd entry, not $HOME, so it is set explicitly.
run() {
	env -u DISPLAY -u WAYLAND_DISPLAY -u XDG_CONFIG_HOME -u XDG_DATA_HOME \
		HOME="$home" JAVA_TOOL_OPTIONS="-Duser.home=$home" \
		"$hammer" "$@"
}

echo "::group::sync status"
run sync status | tee "$home/status.json"
jq -e '.projects == []' "$home/status.json" > /dev/null
echo "::endgroup::"

echo "::group::export with a WebP image"
run project create --name Smoke > /dev/null
image_id="$(run image add --project Smoke --name pic --extension webp --in "$webp" | jq -er '.id')"
scene_id="$(run scene create --project Smoke --name One | jq -er '.id')"
run scene write --project Smoke --id "$scene_id" --mode live --markdown "![image](hammer://image/$image_id)" > /dev/null
run project export --project Smoke --format docx --out "$home/smoke.docx" > /dev/null
if ! unzip -l "$home/smoke.docx" | grep -q 'word/media/image1\.png'; then
	echo "::error::The exported DOCX has no PNG: Skia could not transcode the WebP."
	unzip -l "$home/smoke.docx"
	exit 1
fi
echo "::endgroup::"

echo "CLI smoke test passed: $hammer"
