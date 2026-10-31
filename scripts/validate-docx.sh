#!/usr/bin/env bash
# Validates .docx files against the Open XML SDK schemas. Needs Docker.
# Usage: scripts/validate-docx.sh <file.docx>... [Office2016|Office2019|Microsoft365 ...]
#        scripts/validate-docx.sh --samples [office-version]
# --samples renders the DocxValidationSamples test's documents into a temp dir and validates those.
# Prints the validator's JSON per file; exits non-zero if any file has errors.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"

# Tagged by Dockerfile hash, so Dockerfile edits rebuild the image.
dockerfile="$here/docx-validator/Dockerfile"
if command -v sha256sum >/dev/null 2>&1; then hash="$(sha256sum "$dockerfile")"
else hash="$(shasum -a 256 "$dockerfile")"; fi
IMAGE="hammer-docx-validator:${hash:0:12}"

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
	docker build -q -t "$IMAGE" "$here/docx-validator" >/dev/null
fi

files=()
extra=()
samples=false
for arg in "$@"; do
	if [[ "$arg" == "--samples" ]]; then samples=true
	elif [[ -f "$arg" ]]; then files+=("$(cd "$(dirname "$arg")" && pwd)/$(basename "$arg")")
	else extra+=("$arg"); fi
done

if $samples; then
	dir="$(mktemp -d)"
	trap 'rm -rf "$dir"' EXIT
	# --rerun, because Gradle otherwise skips the test task as up to date.
	(cd "$here/.." && DOCX_SAMPLES_DIR="$dir" ./gradlew -q :common:desktopTest --rerun \
		--tests data.export.DocxValidationSamples)
	shopt -s nullglob
	for f in "$dir"/*.docx; do files+=("$f"); done
	if [[ ${#files[@]} -eq 0 ]]; then
		echo "DocxValidationSamples wrote no samples to $dir" >&2
		exit 1
	fi
fi

if [[ ${#files[@]} -eq 0 ]]; then
	echo "usage: $0 <file.docx>... [office-version]" >&2
	echo "       $0 --samples [office-version]" >&2
	exit 2
fi

status=0
# The +-guard keeps empty arrays from tripping set -u on bash < 4.4.
for f in "${files[@]}"; do
	out="$(docker run --rm -v "$(dirname "$f")":/in:ro "$IMAGE" "/in/$(basename "$f")" ${extra[@]+"${extra[@]}"})"
	echo "== $f"
	echo "$out"
	# The validator prints an empty JSON array when the file is clean.
	if [[ "$(echo "$out" | tr -d '[:space:]')" != "[]" ]]; then status=1; fi
done
exit $status
