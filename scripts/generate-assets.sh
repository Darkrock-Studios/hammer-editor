#!/usr/bin/env bash
# Regenerate all graphical assets from scripts/assets.yaml.
# See DEVELOPMENT.md → "Asset Generation" for details.
#
# Dependencies (Debian/Ubuntu):
#   sudo apt install python3-yaml python3-cairosvg python3-pil icnsutils
#
# Falls back to uv / venv if system Python deps aren't installed.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$ROOT/scripts/generate_assets.py"

# Path 1: system python with all deps already importable (preferred).
if python3 -c "import yaml, cairosvg, PIL" 2>/dev/null; then
    exec python3 "$SCRIPT" "$@"
fi

# Path 2: uv handles deps via the PEP 723 metadata in the script.
if command -v uv >/dev/null; then
    exec uv run "$SCRIPT" "$@"
fi

# Path 3: project-local venv.
VENV="$ROOT/scripts/.venv"
STAMP="$VENV/.deps-installed"
if [[ ! -f "$STAMP" ]]; then
    if ! python3 -c "import venv" 2>/dev/null; then
        echo "error: no usable Python toolchain found." >&2
        echo "install one of:" >&2
        echo "  sudo apt install python3-yaml python3-cairosvg python3-pil  # preferred" >&2
        echo "  sudo apt install python3-venv                               # fallback" >&2
        echo "  install uv (https://docs.astral.sh/uv/)" >&2
        exit 1
    fi
    echo "→ setting up scripts/.venv (one-time)"
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install --quiet --upgrade pip
    "$VENV/bin/pip" install --quiet pyyaml 'cairosvg>=2.7' 'pillow>=10'
    touch "$STAMP"
fi
exec "$VENV/bin/python" "$SCRIPT" "$@"
