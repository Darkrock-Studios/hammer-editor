#!/usr/bin/env bash
# Fixes a jpackage .deb so it installs on Debian and Raspberry Pi OS, desktop or headless, as well
# as on the Ubuntu it was built on:
# - jpackage names the build machine's packages in Depends, which include Ubuntu-only names
#   (libjpeg-turbo8) and 64-bit time_t names (libasound2t64) that Debian 12 lacks.
# - Its postinst and prerm register the menu entry with xdg-desktop-menu, which fails where there
#   is no menu directory, as on a minimal or headless install, and leaves the package broken.
# Usage: deb-make-portable.sh <file.deb>
set -euo pipefail

deb="$1"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

dpkg-deb -R "$deb" "$work/pkg"
control="$work/pkg/DEBIAN/control"

depends="$(sed -n 's/^Depends: //p' "$control")"
portable="$(
	tr ',' '\n' <<< "$depends" | sed 's/^ *//; s/ *$//' | while read -r dep; do
		case "$dep" in
			libjpeg-turbo8) echo "libjpeg-turbo8 | libjpeg62-turbo" ;;
			*t64) echo "$dep | ${dep%t64}" ;;
			*) echo "$dep" ;;
		esac
	done | paste -sd ',' | sed 's/,/, /g'
)"
sed -i "s/^Depends: .*/Depends: $portable/" "$control"

for script in postinst prerm; do
	sed -i 's/^\(xdg-desktop-menu .*\)$/\1 || true/' "$work/pkg/DEBIAN/$script"
done

# jpackage builds under fakeroot, so every file in the package is root's.
dpkg-deb --root-owner-group -b "$work/pkg" "$deb" > /dev/null
echo "Depends: $portable"
