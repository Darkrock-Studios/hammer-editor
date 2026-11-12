#!/usr/bin/env bash
# Installs a .deb in clean Debian 12 and 13 containers, which Raspberry Pi OS 12 and 13 are built
# on, and runs the CLI smoke test there. An Ubuntu runner cannot show what these catch: package
# names Debian lacks, a glibc newer than Debian's, and minimal images with no menu directory.
# Run from the repository root; both paths are relative to it.
# Usage: deb-debian-smoke-test.sh <file.deb> <webp image>
set -euo pipefail

deb="$1"
webp="$2"

for image in debian:bookworm debian:trixie; do
	echo "=== $image"
	docker run --rm -v "$PWD:/src:ro" -w /src "$image" bash -euc '
		apt-get update -qq
		apt-get install -y -qq "./$1" jq unzip
		.github/scripts/cli-smoke-test.sh /opt/hammer/bin/hammer "$2"
		apt-get remove -y -qq hammer' _ "$deb" "$webp"
done
