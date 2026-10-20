"""Snapshot the current grouping study into ui-research/gallery-history/.

Usage: python3 -I ui-research/snapshot-gallery.py <slug> "<what this round tried>"

Copies grouping.html and every image it references from composeUi/build/screen-gallery
into gallery-history/NN-<slug>/, then rebuilds gallery-history/index.html.
"""
import datetime
import html
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GALLERY = ROOT / "composeUi/build/screen-gallery"
HISTORY = ROOT / "ui-research/gallery-history"


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    slug = re.sub(r"[^a-z0-9]+", "-", sys.argv[1].lower()).strip("-")
    note = sys.argv[2]

    HISTORY.mkdir(parents=True, exist_ok=True)
    existing = sorted(p for p in HISTORY.iterdir() if p.is_dir() and re.match(r"\d\d-", p.name))
    number = int(existing[-1].name[:2]) + 1 if existing else 1
    dest = HISTORY / f"{number:02d}-{slug}"
    dest.mkdir()

    page = (GALLERY / "grouping.html").read_text()
    refs = set(re.findall(r'(?:src|href)="([^"#:]+\.png)"', page))
    for ref in refs:
        target = dest / ref
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(GALLERY / ref, target)
    page = page.replace('<a href="index.html">All screens</a>', '<a href="../index.html">History</a>')
    (dest / "grouping.html").write_text(page)
    (dest / "note.txt").write_text(f"{datetime.date.today().isoformat()}\n{note}\n")

    write_index()
    print(f"Snapshot {dest.name}: {len(refs)} images")


def write_index():
    rows = []
    for snap in sorted(p for p in HISTORY.iterdir() if p.is_dir()):
        date, _, note = (snap / "note.txt").read_text().partition("\n")
        rows.append(
            f'<li><a href="{snap.name}/grouping.html">{html.escape(snap.name)}</a> '
            f'<span class="date">{html.escape(date)}</span><br>{html.escape(note.strip())}</li>'
        )
    (HISTORY / "index.html").write_text(
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>Grouping study history</title>"
        "<style>body{font:15px/1.5 system-ui,sans-serif;margin:32px;max-width:760px;background:#f4f4f2;color:#222}"
        "li{margin:14px 0}.date{color:#888;font-size:13px;margin-left:8px}</style></head><body>"
        "<h1>Grouping study history</h1><ol>" + "\n".join(rows) + "</ol></body></html>\n"
    )


if __name__ == "__main__":
    main()
