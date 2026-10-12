#!/bin/sh
# Builds the host's test fixtures into ../resources/plugins. Needs wabt's wat2wasm, and for the gc_*.wat
# fixtures binaryen's wasm-as (on PATH, or set WASM_AS), since wabt cannot assemble Wasm GC types.
set -e
cd "$(dirname "$0")"
out=../resources/plugins
wasm_as="${WASM_AS:-wasm-as}"
command -v wat2wasm "$wasm_as" >/dev/null || { echo "Needs wat2wasm and $wasm_as" >&2; exit 1; }
mkdir -p "$out"
for f in *.wat; do
	case "$f" in
		gc_*) "$wasm_as" --enable-gc --enable-reference-types "$f" -o "$out/$(basename "$f" .wat).wasm" ;;
		*) wat2wasm "$f" -o "$out/$(basename "$f" .wat).wasm" ;;
	esac
done
echo "Built $(ls *.wat | wc -l) fixtures into $out"
