#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = [
#   "pyyaml>=6",
#   "cairosvg>=2.7",
#   "pillow>=10",
# ]
# ///
"""Generate all graphical assets from scripts/assets.yaml.

Schema is documented in DEVELOPMENT.md under "Asset Generation".
"""
from __future__ import annotations

import cairosvg
import io
import shutil
import subprocess
import sys
import tempfile
import yaml
from PIL import Image, ImageDraw, ImageFont
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts" / "assets.yaml"


@dataclass
class Context:
	sources: dict[str, Path]
	fonts: dict[str, Path]
	palette: dict[str, str]
	layouts: dict[str, dict[str, Any]]


def main() -> int:
	with MANIFEST.open() as f:
		m = yaml.safe_load(f)

	ctx = Context(
		sources={k: ROOT / v for k, v in m["sources"].items()},
		fonts={k: ROOT / v for k, v in m["fonts"].items()},
		palette=m["palette"],
		layouts=m.get("layouts", {}),
	)

	for src in ctx.sources.values():
		if not src.exists():
			sys.exit(f"error: source not found: {src}")
	for font in ctx.fonts.values():
		if not font.exists():
			sys.exit(f"error: font not found: {font}")

	for asset in m["assets"]:
		kind = asset["type"]
		asset_id = asset.get("id", "?")
		handler = HANDLERS.get(kind)
		if handler is None:
			sys.exit(f"asset {asset_id!r}: unknown type {kind!r}")
		alpha = asset.get("alpha", "keep")
		if alpha not in ("keep", "strip"):
			sys.exit(f"asset {asset_id!r}: alpha must be 'keep' or 'strip', got {alpha!r}")
		target = asset.get("out") or asset.get("out-dir") or "?"
		print(f"  {asset_id:26} → {target}")
		try:
			handler(asset, ctx)
		except KeyError as e:
			sys.exit(f"asset {asset_id!r}: missing required key {e}")

	print("\ndone.")
	return 0


# ---------------------------------------------------------------------------
# rendering primitives
# ---------------------------------------------------------------------------

def render_svg(ctx: Context, src_name: str, width: int, height: int) -> Image.Image:
	"""Rasterize an SVG source to an RGBA Pillow image at exact pixel dims."""
	svg_path = ctx.sources[src_name]
	png_bytes = cairosvg.svg2png(
		url=str(svg_path), output_width=width, output_height=height
	)
	return Image.open(io.BytesIO(png_bytes)).convert("RGBA")


def write_png(img: Image.Image, out: Path, alpha: str = "keep") -> None:
	out.parent.mkdir(parents=True, exist_ok=True)
	if alpha == "strip":
		# Some stores (Apple marketing icon, Play featureGraphic) reject any
		# alpha channel — must flatten and re-encode without alpha.
		if img.mode == "RGBA":
			bg = Image.new("RGB", img.size, (0, 0, 0))
			bg.paste(img, mask=img.split()[3])
			img = bg
		else:
			img = img.convert("RGB")
	img.save(out, "PNG", optimize=True)


def resolve_color(ctx: Context, color: str) -> tuple[int, int, int, int]:
	if color in ctx.palette:
		color = ctx.palette[color]
	if color.startswith("#"):
		h = color[1:]
		if len(h) == 6:
			return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
		if len(h) == 8:
			r, g, b, a = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
			return (r, g, b, a)
	raise ValueError(f"can't resolve color: {color!r}")


def recolor(img: Image.Image, color: tuple[int, int, int, int]) -> Image.Image:
	"""Replace all RGB with `color` while preserving the original alpha.

	Assumes a single-hue source — any source color information is discarded,
	so this would flatten multi-color art to a monochrome silhouette.
	"""
	img = img.convert("RGBA")
	alpha = img.split()[3]
	out = Image.new("RGBA", img.size, color[:3] + (0,))
	out.putalpha(alpha)
	return out


def fit_font_to_height(font_path: Path, text: str, target_h: int) -> ImageFont.FreeTypeFont:
	"""Pick the largest TTF size where the rendered text height ≤ target_h."""
	lo, hi = 4, max(8, target_h * 4)
	# Start with the smallest size — guaranteed to fit if any size does.
	best = ImageFont.truetype(str(font_path), lo)
	while lo <= hi:
		mid = (lo + hi) // 2
		font = ImageFont.truetype(str(font_path), mid)
		bbox = font.getbbox(text)
		if bbox[3] - bbox[1] <= target_h:
			best = font
			lo = mid + 1
		else:
			hi = mid - 1
	return best


_TRIMMED_ICON_CACHE: dict[tuple[str, str | None], Image.Image] = {}


def trimmed_icon(ctx: Context, source: str, color: str | None) -> Image.Image:
	"""Rasterize an icon SVG at high-res, optionally recolor, trim transparent
	borders. Cached because every composition rebuilds the same 1024px base.

	Trimming makes layout `height-pct` reflect the visible glyph rather than
	the SVG's empty viewBox padding.
	"""
	key = (source, color)
	cached = _TRIMMED_ICON_CACHE.get(key)
	if cached is not None:
		return cached.copy()
	img = render_svg(ctx, source, 1024, 1024)
	if color is not None:
		img = recolor(img, resolve_color(ctx, color))
	bbox = img.getbbox()
	if bbox:
		img = img.crop(bbox)
	_TRIMMED_ICON_CACHE[key] = img
	return img.copy()


def render_layout(ctx: Context, layout_name: str, w: int, h: int) -> Image.Image:
	"""Render a named composition (icon + optional wordmark) at w×h."""
	layout = ctx.layouts[layout_name]
	bg_color = resolve_color(ctx, layout.get("background", "#00000000"))
	canvas = Image.new("RGBA", (w, h), bg_color)

	padding = int(min(w, h) * layout.get("padding-pct", 0) / 100.0)
	avail_w = max(1, w - 2 * padding)
	avail_h = max(1, h - 2 * padding)

	icon_spec = layout["icon"]
	raw = trimmed_icon(ctx, icon_spec["source"], icon_spec.get("color"))
	icon_target_h = max(1, int(avail_h * icon_spec["height-pct"] / 100.0))
	aspect = raw.width / raw.height
	icon_img = raw.resize((max(1, int(icon_target_h * aspect)), icon_target_h),
	                      Image.LANCZOS)

	text_spec = layout.get("text")
	if not text_spec:
		canvas.alpha_composite(
			icon_img, ((w - icon_img.width) // 2, (h - icon_img.height) // 2)
		)
		return canvas

	font_path = ctx.fonts[text_spec["font"]]
	text_color = resolve_color(ctx, text_spec["color"])
	text_target_h = max(4, int(avail_h * text_spec["size-pct"] / 100.0))
	gap = int(w * layout.get("gap-pct", 0) / 100.0)

	def measure(font: ImageFont.FreeTypeFont) -> tuple[int, int, int, int]:
		bbox = font.getbbox(text_spec["content"])
		return bbox[2] - bbox[0], bbox[3] - bbox[1], -bbox[0], -bbox[1]

	font = fit_font_to_height(font_path, text_spec["content"], text_target_h)
	text_w, text_h, text_ox, text_oy = measure(font)

	# Preserve icon:text proportions: if the strip overflows the canvas width,
	# scale icon, gap, and font by the same factor.
	total_w = icon_img.width + gap + text_w
	if total_w > avail_w:
		scale = avail_w / total_w
		icon_img = icon_img.resize(
			(max(1, int(icon_img.width * scale)),
			 max(1, int(icon_img.height * scale))),
			Image.LANCZOS,
		)
		gap = int(gap * scale)
		font = fit_font_to_height(font_path, text_spec["content"],
		                          max(4, int(text_target_h * scale)))
		text_w, text_h, text_ox, text_oy = measure(font)
		total_w = icon_img.width + gap + text_w

	start_x = max(padding, (w - total_w) // 2)
	canvas.alpha_composite(icon_img, (start_x, (h - icon_img.height) // 2))
	ImageDraw.Draw(canvas).text(
		(start_x + icon_img.width + gap + text_ox, (h - text_h) // 2 + text_oy),
		text_spec["content"], font=font, fill=text_color,
	)
	return canvas


# ---------------------------------------------------------------------------
# asset-type handlers
# ---------------------------------------------------------------------------

def handle_icon(asset: dict[str, Any], ctx: Context) -> None:
	size = asset["size"]
	img = render_svg(ctx, asset["source"], size, size)
	write_png(img, ROOT / asset["out"], asset.get("alpha", "keep"))


def handle_ico(asset: dict[str, Any], ctx: Context) -> None:
	sizes = asset["sizes"]
	base = render_svg(ctx, asset["source"], max(sizes), max(sizes))
	out = ROOT / asset["out"]
	out.parent.mkdir(parents=True, exist_ok=True)
	base.save(out, "ICO", sizes=[(s, s) for s in sizes])


def handle_icns(asset: dict[str, Any], ctx: Context) -> None:
	if not shutil.which("png2icns"):
		sys.exit("error: png2icns not found. install: sudo apt install icnsutils")
	sizes = asset["sizes"]
	out = ROOT / asset["out"]
	out.parent.mkdir(parents=True, exist_ok=True)
	# png2icns wants files on disk; render the largest once and downscale.
	base = render_svg(ctx, asset["source"], max(sizes), max(sizes))
	with tempfile.TemporaryDirectory() as td:
		paths = []
		for s in sizes:
			p = Path(td) / f"icon-{s}.png"
			(base if s == max(sizes)
			 else base.resize((s, s), Image.LANCZOS)).save(p, "PNG")
			paths.append(str(p))
		subprocess.run(["png2icns", str(out), *paths], check=True,
		               stdout=subprocess.DEVNULL)


def handle_composition(asset: dict[str, Any], ctx: Context) -> None:
	w, h = asset["size"]
	img = render_layout(ctx, asset["layout"], w, h)
	write_png(img, ROOT / asset["out"], asset.get("alpha", "keep"))


def handle_msix_scale(asset: dict[str, Any], ctx: Context) -> None:
	base_w, base_h = asset["base"]
	name = asset["name"]
	out_dir = ROOT / asset["out-dir"]
	out_dir.mkdir(parents=True, exist_ok=True)
	layout_name = asset.get("layout")
	for scale in asset["scales"]:
		f = scale / 100.0
		w = round(base_w * f)
		h = round(base_h * f)
		if layout_name:
			img = render_layout(ctx, layout_name, w, h)
		else:
			img = render_svg(ctx, asset["source"], w, h)
		# MRT convention: scale-100 is the unsuffixed default that the manifest
		# references (e.g. Assets/StoreLogo.png); higher scales add a suffix.
		suffix = "" if scale == 100 else f".scale-{scale}"
		out = out_dir / f"{name}{suffix}.png"
		write_png(img, out, asset.get("alpha", "keep"))


HANDLERS: dict[str, Callable[[dict[str, Any], Context], None]] = {
	"icon": handle_icon,
	"ico": handle_ico,
	"icns": handle_icns,
	"composition": handle_composition,
	"msix-scale": handle_msix_scale,
}

if __name__ == "__main__":
	sys.exit(main())
