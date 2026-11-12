#!/usr/bin/env bash
# Downloads LanguageTool n-gram data into ./ngrams for the languagetool service.
# Usage: ./download-ngrams.sh en [de fr ...]   (English is about 8.5 GB unzipped)
set -euo pipefail

BASE_URL="https://languagetool.org/download/ngram-data"
TARGET="$(cd "$(dirname "$0")" && pwd)/ngrams"

if [ "$#" -eq 0 ]; then
	echo "Usage: $0 <language> [<language> ...]   e.g. $0 en" >&2
	exit 1
fi

mkdir -p "$TARGET"
if [ ! -w "$TARGET" ]; then
	# Docker creates a missing bind-mount directory as root.
	echo "$TARGET is not writable; run: sudo chown -R \"\$(id -u):\$(id -g)\" \"$TARGET\"" >&2
	exit 1
fi

listing="$(curl -fsSL "$BASE_URL/")"

for lang in "$@"; do
	if [[ ! "$lang" =~ ^[a-z]{2,3}$ ]]; then
		echo "$lang: expected a language code such as en" >&2
		exit 1
	fi
	if [ -d "$TARGET/$lang" ]; then
		echo "$lang: already in $TARGET/$lang, skipping"
		continue
	fi
	file="$(grep -o "ngrams-${lang}-[0-9]*\.zip" <<<"$listing" | sort -u | tail -n 1)"
	if [ -z "$file" ]; then
		echo "$lang: no n-gram data at $BASE_URL" >&2
		exit 1
	fi
	echo "$lang: downloading $file"
	curl -fL --continue-at - -o "$TARGET/$file" "$BASE_URL/$file"
	# Unzip beside the target and move into place, so an interrupted run leaves no partial language.
	staging="$TARGET/.unzip-$lang"
	rm -rf "$staging"
	unzip -q "$TARGET/$file" -d "$staging"
	mv "$staging/$lang" "$TARGET/$lang"
	rm -rf "$staging" "$TARGET/$file"
done

# The container runs as a non-root user that must be able to read the data.
chmod -R a+rX "$TARGET"
echo "Done. Restart the languagetool service to load it."
