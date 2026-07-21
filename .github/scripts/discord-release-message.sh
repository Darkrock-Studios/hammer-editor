#!/usr/bin/env bash
#
# Builds the Discord announcement `content` for a release and prints it to
# stdout. Shared by the automated notify job (publish-release.yml) and the
# manual announcer (announce-release.yml) so the token catalog, message format,
# and length handling live in exactly one place and can't drift apart.
#
# Required env:
#   TAG           - the release tag (may carry a +suffix for subset releases)
#   RELEASE_NAME  - release title
#   RELEASE_URL   - release html_url
#   RELEASE_BODY  - release body / changelog
set -euo pipefail

# Tokens → human display names for the Discord audience. Keep in sync with the
# Platform token catalog; this is the single source for release messaging.
to_display() {
  case "$1" in
    google-play)   echo "Google Play" ;;
    fdroid)        echo "F-Droid" ;;
    snap)          echo "Snap" ;;
    ms-store)      echo "MS Store" ;;
    mac-app-store) echo "Mac App Store" ;;
    ios-app-store) echo "iOS App Store" ;;
    server)        echo "Server" ;;
    *)             echo "$1" ;;
  esac
}

# Subset releases carry a +token(+token…) suffix; name them in a header line.
header=""
if [[ "${TAG:-}" == *+* ]]; then
  pretty=""
  for token in $(echo "${TAG#*+}" | tr '+' ' '); do
    display="$(to_display "$token")"
    if [ -z "$pretty" ]; then pretty="$display"; else pretty="$pretty, $display"; fi
  done
  header="🔧 Patch release — ${pretty} only"$'\n'
fi

content="$(printf '%s**New release:** %s\n**Download Here:** %s\n\n**Changelog:**\n%s' \
  "$header" "${RELEASE_NAME:-}" "${RELEASE_URL:-}" "${RELEASE_BODY:-}")"

# Discord caps webhook content at 2000 characters. Trim by Unicode codepoint
# with jq (locale-independent, and never splits a multibyte character) rather
# than eating a 400 from the API.
printf '%s' "$content" | jq -Rrs 'if length > 2000 then .[0:1997] + "..." else . end'
