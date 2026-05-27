# Asset Generation

All graphical assets — app icons, store-listing graphics, MSIX tiles, favicons,
the Play Store feature graphic, the Snap featured banner, etc. — are generated
from a single manifest at `scripts/assets.yaml`. Run:

```
scripts/generate-assets.sh
```

This (re)writes every output declared in the manifest. Outputs land in one of
two places:

- **Tracked paths** (e.g. `snap/gui/`, `msix/Assets/`, `fastlane/metadata/...`,
  `ios/.../AppIcon.appiconset/`, `desktop/icons/`, server resources) — consumed
  by build tooling at compile/package time. Committed to the repo.
- **`build/store-assets/`** (gitignored — `build/` is already in `.gitignore`) —
  uploaded by hand to store dashboards. Regenerate before each upload.

## Dependencies

Python 3.10+ plus three libraries. The preferred path uses the Debian packages:

```
sudo apt install python3-yaml python3-cairosvg python3-pil icnsutils
```

If those aren't available, the wrapper falls back to `uv` (if installed) or a
project-local venv at `scripts/.venv/`. The Python script itself carries PEP
723 inline metadata so `uv run scripts/generate_assets.py` also works directly.

## Manifest structure (`scripts/assets.yaml`)

```yaml
sources: # named SVG inputs
	logo: hammer-editor-logo.svg        # hammer with grey rounded-rect bg
	icon: hammer-icon.svg               # hammer alone, transparent

fonts: # named TTF inputs
	kingthings-trypewriter: server/.../Kingthings_Trypewriter_2.ttf

palette: # named colors (referenced by name in layouts)
	brand-orange: "#B7410E"
	brand-dark: "#2A2F2D"

layouts: # reusable icon+text compositions
	hammer-wordmark:
		background: brand-dark
		padding-pct: 8
		icon: { source: icon, color: brand-orange, height-pct: 90 }
		text: { content: "Hammer", font: kingthings-trypewriter,
		        color: brand-orange, size-pct: 45 }
		gap-pct: 12

assets: # individual asset entries
	- { id: snap-desktop, type: icon, source: logo, size: 256,
	    out: snap/gui/hammer-editor.png }
	- { id: play-feature-graphic, type: composition, layout: hammer-wordmark,
	    size: [ 1024, 500 ], alpha: strip,
	    out: fastlane/metadata/android/en-US/images/featureGraphic.png }
```

## Asset types

| `type:`       | Produces                                                                                                                                                                                                                      |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `icon`        | A single PNG at `size:` from `source:`                                                                                                                                                                                        |
| `ico`         | Multi-resolution `.ico` containing each entry in `sizes:`                                                                                                                                                                     |
| `icns`        | Multi-resolution `.icns` (requires `png2icns` from icnsutils)                                                                                                                                                                 |
| `composition` | Renders a named `layout:` at `size: [w, h]`                                                                                                                                                                                   |
| `msix-scale`  | One PNG per scale in `scales:` (e.g. `[100, 200]`). scale=100 writes the unsuffixed default (`Square150x150Logo.png`) that the appxmanifest references; higher scales use the MRT suffix (`Square150x150Logo.scale-200.png`). |

## Per-asset options

- `alpha: keep` (default) or `alpha: strip` — strip is required for Apple's
  1024 marketing icon and Play's `featureGraphic` (both auto-reject PNGs with
  any alpha channel).
- `out:` is a path relative to the repo root. Anything under `build/` is
  gitignored.

## Layout sizing

Layout dimensions are percentages of the output canvas, so the same
`hammer-wordmark` composition produces a sharp 310×150 MSIX wide tile, a 1024×500
Play feature graphic, and a 1920×640 Snap banner with no per-size tuning. The
icon is auto-trimmed to its non-transparent bounding box (so `height-pct`
reflects the visible hammer, not the SVG's empty viewBox padding), and the
whole icon+gap+text strip is auto-scaled down if it would overflow the canvas
width.

## Adding a new asset

1. Add an entry under `assets:` in `scripts/assets.yaml`.
2. Run `scripts/generate-assets.sh`.
3. If the new asset uses a new layout, font, or palette color, declare it in
   the corresponding top-level section first.

## Swapping the wordmark font, brand color, or icon SVG

Edit the `fonts:`, `palette:`, or `sources:` section in `scripts/assets.yaml`,
then re-run `scripts/generate-assets.sh`. Every downstream asset that references
the changed value will pick it up.
