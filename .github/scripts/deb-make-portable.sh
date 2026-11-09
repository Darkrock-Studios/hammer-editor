#!/usr/bin/env bash
# Fixes a jpackage .deb so it installs on Debian and Raspberry Pi OS, desktop or headless, as well
# as on the Ubuntu it was built on:
# - jpackage names the build machine's packages in Depends, which include Ubuntu-only names
#   (libjpeg-turbo8) and 64-bit time_t names (libasound2t64) that Debian 12 lacks.
# - Its postinst and prerm register the menu entry with xdg-desktop-menu, which fails where there
#   is no menu directory, as on a minimal or headless install, and leaves the package broken.
# Usage: deb-make-portable.sh <file.deb>
set -euo pipefail

deb="$(realpath "$1")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Only the control archive changes. The payload member is copied as it is, so it is not
# recompressed and its ownership and modes stay exactly as jpackage wrote them.
cd "$work"
ar x "$deb"
data="$(ls data.tar*)"
mkdir pkg
dpkg-deb --ctrl-tarfile "$deb" | tar -x -C pkg
control=pkg/control

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
	sed -i 's/^\(xdg-desktop-menu .*\)$/\1 || true/' "pkg/$script"
done

# dpkg reads an uncompressed control.tar too, which needs no compressor on the build machine.
tar -C pkg -cf control.tar --owner=0 --group=0 --numeric-owner .
ar rc rebuilt.deb debian-binary control.tar "$data"
mv rebuilt.deb "$deb"
echo "Depends: $portable"
