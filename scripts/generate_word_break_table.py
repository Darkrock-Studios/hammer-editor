#!/usr/bin/env python3
"""Generate the UAX #29 word break table used by base's word segmentation.

Run from anywhere, with either a directory holding WordBreakProperty.txt, WordBreakTest.txt,
emoji-data.txt and UnicodeData.txt, or a Unicode version to download them:

	python3 scripts/generate_word_break_table.py --ucd-dir path/to/ucd
	python3 scripts/generate_word_break_table.py --unicode-version 17.0.0

Writes base/.../markdown/WordBreakProperties.kt and copies WordBreakTest.txt verbatim into
base/src/commonTest/resources/ for the conformance test. Python 3 standard library only.
"""
from __future__ import annotations

import argparse
import http.client
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KOTLIN_OUT = ROOT / "base/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/base/markdown/WordBreakProperties.kt"
TEST_OUT = ROOT / "base/src/commonTest/resources/WordBreakTest.txt"
CACHE = Path.home() / ".cache" / "hammer-ucd"
UCD_HOST = "www.unicode.org"

FILES = {
	"WordBreakProperty.txt": "ucd/auxiliary/WordBreakProperty.txt",
	"WordBreakTest.txt": "ucd/auxiliary/WordBreakTest.txt",
	"emoji-data.txt": "ucd/emoji/emoji-data.txt",
	"UnicodeData.txt": "ucd/UnicodeData.txt",
}

# Order defines the Kotlin constant values; OTHER must stay 0 (the value of unlisted code points).
WB_VALUES = [
	("Other", "WB_OTHER"),
	("CR", "WB_CR"),
	("LF", "WB_LF"),
	("Newline", "WB_NEWLINE"),
	("Extend", "WB_EXTEND"),
	("ZWJ", "WB_ZWJ"),
	("Regional_Indicator", "WB_REGIONAL_INDICATOR"),
	("Format", "WB_FORMAT"),
	("Katakana", "WB_KATAKANA"),
	("Hebrew_Letter", "WB_HEBREW_LETTER"),
	("ALetter", "WB_ALETTER"),
	("Single_Quote", "WB_SINGLE_QUOTE"),
	("Double_Quote", "WB_DOUBLE_QUOTE"),
	("MidNumLet", "WB_MID_NUM_LET"),
	("MidLetter", "WB_MID_LETTER"),
	("MidNum", "WB_MID_NUM"),
	("Numeric", "WB_NUMERIC"),
	("ExtendNumLet", "WB_EXTEND_NUM_LET"),
	("WSegSpace", "WB_WSEG_SPACE"),
]
WB_INDEX = {name: i for i, (name, _) in enumerate(WB_VALUES)}
WB_MASK = 0x1F
FLAG_EXTENDED_PICTOGRAPHIC = 0x20
FLAG_LETTER = 0x40
WORD_BEARING = {WB_INDEX[n] for n in ("ALetter", "Hebrew_Letter", "Numeric", "Katakana")}
MAX_CODE_POINT = 0x10FFFF


def parse_ranges(path: Path):
	"""Yields (first, last, value) from a UCD `range ; value # comment` file."""
	for line in path.read_text(encoding="utf-8").splitlines():
		line = line.split("#", 1)[0].strip()
		if not line:
			continue
		cps, value = (part.strip() for part in line.split(";")[:2])
		first, _, last = cps.partition("..")
		yield int(first, 16), int(last or first, 16), value


def ucd_version(path: Path) -> str:
	match = re.match(r"# WordBreakProperty-(\d+\.\d+\.\d+)\.txt", path.read_text(encoding="utf-8"))
	if not match:
		sys.exit(f"{path}: no version header")
	return match.group(1)


def download(version: str) -> Path:
	target = CACHE / version
	target.mkdir(parents=True, exist_ok=True)
	for name, remote in FILES.items():
		dest = target / name
		if not dest.exists():
			path = f"/Public/{version}/{remote}"
			url = f"https://{UCD_HOST}{path}"
			print(f"Downloading {url}")
			connection = http.client.HTTPSConnection(UCD_HOST)
			try:
				connection.request("GET", path)
				response = connection.getresponse()
				body = response.read()
				if response.status != 200:
					sys.exit(f"{url}: HTTP {response.status} {response.reason}")
			finally:
				connection.close()
			dest.write_bytes(body)
	return target


def build_values(ucd: Path) -> list[int]:
	values = [0] * (MAX_CODE_POINT + 1)

	for first, last, name in parse_ranges(ucd / "WordBreakProperty.txt"):
		wb = WB_INDEX[name]
		for cp in range(first, last + 1):
			if values[cp] != 0:
				raise ValueError(f"U+{cp:04X} has two word break values")
			values[cp] = wb

	for first, last, name in parse_ranges(ucd / "emoji-data.txt"):
		if name == "Extended_Pictographic":
			for cp in range(first, last + 1):
				values[cp] |= FLAG_EXTENDED_PICTOGRAPHIC

	range_first = None
	for line in (ucd / "UnicodeData.txt").read_text(encoding="utf-8").splitlines():
		fields = line.split(";")
		cp, name, category = int(fields[0], 16), fields[1], fields[2]
		if name.endswith(", First>"):
			range_first = cp
			continue
		first = range_first if name.endswith(", Last>") else cp
		range_first = None
		for c in range(first, cp + 1):
			wb = values[c] & WB_MASK
			if category == "Nd" and wb != WB_INDEX["Numeric"]:
				raise ValueError(f"U+{c:04X} is Nd but not Numeric")
			# The flag only matters where the word break value does not already make a word.
			if category[0] == "L" and wb not in WORD_BEARING:
				values[c] |= FLAG_LETTER
	return values


def to_runs(values: list[int]) -> list[tuple[int, int, int]]:
	runs = []
	cp = 0
	while cp <= MAX_CODE_POINT:
		value = values[cp]
		end = cp
		while end < MAX_CODE_POINT and values[end + 1] == value:
			end += 1
		if value != 0:
			runs.append((cp, end, value))
		cp = end + 1
	for (_, a_last, _), (b_first, _, _) in zip(runs, runs[1:]):
		if a_last >= b_first:
			raise ValueError("runs overlap")
	return runs


def render(version: str, runs: list[tuple[int, int, int]]) -> str:
	lines = [
		f"// Generated by scripts/generate_word_break_table.py from Unicode {version}. Do not edit.",
		"package com.darkrockstudios.apps.hammer.base.markdown",
		"",
		"// Word_Break property values (UAX #29), in the low bits of a WORD_BREAK_RANGES value.",
	]
	for i, (_, const) in enumerate(WB_VALUES):
		lines.append(f"internal const val {const} = {i}")
	lines += [
		"",
		f"internal const val WB_MASK = 0x{WB_MASK:X}",
		"",
		"/** Extended_Pictographic, from emoji-data.txt. */",
		f"internal const val WB_FLAG_EXTENDED_PICTOGRAPHIC = 0x{FLAG_EXTENDED_PICTOGRAPHIC:X}",
		"",
		"/** A General_Category letter whose Word_Break value is not ALetter, Hebrew_Letter, Katakana or Numeric (Han, kana, Thai...). */",
		f"internal const val WB_FLAG_LETTER = 0x{FLAG_LETTER:X}",
		"",
		"/** Runs of code points as first, last and value (Word_Break value or flags), in order; a code point in none is WB_OTHER. */",
		"internal val WORD_BREAK_RANGES = intArrayOf(",
	]
	per_line = 6
	for i in range(0, len(runs), per_line):
		chunk = runs[i:i + per_line]
		lines.append("\t" + " ".join(f"0x{a:X}, 0x{b:X}, 0x{v:X}," for a, b, v in chunk))
	lines.append(")")
	return "\n".join(lines) + "\n"


def main() -> int:
	parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	source = parser.add_mutually_exclusive_group(required=True)
	source.add_argument("--ucd-dir", type=Path, help="directory holding the four Unicode data files")
	source.add_argument("--unicode-version", help=f"download the files for this version into {CACHE}")
	args = parser.parse_args()

	ucd = args.ucd_dir if args.ucd_dir else download(args.unicode_version)
	version = ucd_version(ucd / "WordBreakProperty.txt")
	runs = to_runs(build_values(ucd))

	KOTLIN_OUT.write_text(render(version, runs), encoding="utf-8")
	shutil.copyfile(ucd / "WordBreakTest.txt", TEST_OUT)
	print(f"Wrote {len(runs)} runs from Unicode {version} to {KOTLIN_OUT.relative_to(ROOT)}")
	print(f"Copied WordBreakTest.txt to {TEST_OUT.relative_to(ROOT)}")
	return 0


if __name__ == "__main__":
	sys.exit(main())
