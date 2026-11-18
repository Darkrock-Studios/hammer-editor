"""Local server for the screenshot markup tool.

Serves ui-research/ on 127.0.0.1, lists look book shots, and writes saved reviews
(annotated PNG + Markdown notes) to ui-research/markup/reviews/.
Run: python3 -I ui-research/markup/serve.py [port] [live gallery dir]
The live dir (a gallery render output) shows as the "live" set and is re-read on every request.
"""
import base64
import csv
import datetime
import http.server
import json
import math
import os
import re
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REVIEWS = ROOT / "markup" / "reviews"
LOOKBOOK = ROOT / "lookbook"
GALLERY_SCALE = 2  # gallery renders at 2x; text inventories are in dp
LIVE = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else None
LIVE_PREFIX = "live/"


def list_shots():
    shots = []
    for png in sorted(LOOKBOOK.glob("*/*/*.png")):
        rel = png.relative_to(ROOT).as_posix()
        set_name, variant = png.parent.parent.name, png.parent.name
        shots.append({"path": rel, "set": set_name, "variant": variant, "name": png.stem, "mtime": png.stat().st_mtime})
    if LIVE and LIVE.is_dir():
        for png in sorted(LIVE.glob("*/*.png")):
            rel = LIVE_PREFIX + png.relative_to(LIVE).as_posix()
            shots.append({"path": rel, "set": "live", "variant": png.parent.name, "name": png.stem, "mtime": png.stat().st_mtime})
    return shots


def resolve(path):
    """Maps a shot path to a file: "live/..." into the live dir, anything else under ui-research."""
    if path.startswith(LIVE_PREFIX):
        if not LIVE:
            return None
        base, rel = LIVE, path[len(LIVE_PREFIX):]
    else:
        base, rel = ROOT, path
    p = (base / rel).resolve()
    return p if base in p.parents else None


def safe_source(path):
    if not path:
        return None
    p = resolve(path)
    return p if p and p.is_file() else None


def text_inventory(source):
    base = source.name.removesuffix(".png").removesuffix(".full")
    tsv = source.parent / f"{base}.text.tsv"
    if not tsv.is_file():
        return []
    rows = []
    with tsv.open(newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            try:
                top = float(row["top_dp"]) * GALLERY_SCALE
                left = float(row["left_dp"]) * GALLERY_SCALE
            except (KeyError, ValueError):
                continue
            rows.append((left, top, row.get("text", "")))
    return rows


def texts_for_mark(mark, inventory):
    if not inventory:
        return []
    if mark["kind"] == "pin":
        x, y = mark["x"], mark["y"]
        ranked = sorted(inventory, key=lambda t: math.hypot(t[0] - x, (t[1] + 12) - y))
        return [t[2] for t in ranked[:2] if math.hypot(t[0] - x, (t[1] + 12) - y) < 260]
    x0, y0 = mark["x"], mark["y"]
    x1, y1 = x0 + mark["w"], y0 + mark["h"]
    # Text nodes are recorded by their top-left corner; nudge to the first line's middle.
    return [t[2] for t in inventory if x0 - 8 <= t[0] + 8 <= x1 and y0 - 8 <= t[1] + 12 <= y1][:12]


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:60] or "review"


def save_review(payload):
    REVIEWS.mkdir(parents=True, exist_ok=True)
    source = safe_source(payload.get("source"))
    title = payload.get("title") or (source.stem if source else "pasted")
    stamp = datetime.datetime.now().strftime("%Y-%m-%d-%H%M%S")
    stem = f"{stamp}-{slug(title)}"

    data_url = payload.get("png", "")
    if not data_url.startswith("data:image/png;base64,"):
        raise ValueError("png must be a PNG data URL")
    (REVIEWS / f"{stem}.png").write_bytes(base64.b64decode(data_url.split(",", 1)[1]))

    inventory = text_inventory(source) if source else []
    lines = [f"# Review: {title}", ""]
    if source:
        lines.append(f"Source: `{payload.get('source')}`")
    lines += [f"Saved: {stamp}", "", f"![marked up]({stem}.png)", ""]
    general = (payload.get("general") or "").strip()
    if general:
        lines += ["## Overall", "", general, ""]
    lines += ["## Marks", ""]
    for mark in payload.get("marks", []):
        where = (
            f"pin at ({mark['x']:.0f}, {mark['y']:.0f})"
            if mark["kind"] == "pin"
            else f"{mark['kind']} ({mark['x']:.0f}, {mark['y']:.0f}) {mark['w']:.0f}x{mark['h']:.0f}"
        )
        note = (mark.get("note") or "").strip() or "(no note)"
        lines.append(f"{mark['n']}. **{note}**  ")
        lines.append(f"   {where} px")
        texts = texts_for_mark(mark, inventory)
        if texts:
            lines.append("   Text there: " + "; ".join(f"`{t}`" for t in texts))
        lines.append("")
    (REVIEWS / f"{stem}.md").write_text("\n".join(lines))
    return f"markup/reviews/{stem}.md"


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def send_json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/":
            self.send_response(302)
            self.send_header("Location", "/markup/index.html")
            self.end_headers()
        elif self.path == "/api/shots":
            self.send_json(200, list_shots())
        elif self.path.startswith("/" + LIVE_PREFIX):
            p = resolve(urllib.parse.unquote(self.path.split("?", 1)[0][1:]))
            if not p or not p.is_file():
                self.send_error(404)
                return
            data = p.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "image/png" if p.suffix == ".png" else "text/plain")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            super().do_GET()

    def do_POST(self):
        if self.path != "/api/save":
            self.send_json(404, {"error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length > 60_000_000:
                raise ValueError("too large")
            saved = save_review(json.loads(self.rfile.read(length)))
            self.send_json(200, {"saved": saved})
        except Exception as e:  # noqa: BLE001 - report any failure to the page
            self.send_json(400, {"error": str(e)})


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
    print(f"Markup tool: http://127.0.0.1:{port}/", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
