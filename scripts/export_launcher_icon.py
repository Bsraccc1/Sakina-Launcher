"""Export app launcher icons from the Sakinah branding source image."""
from __future__ import annotations

import os
from pathlib import Path

from PIL import Image

SRC = Path(
    r"C:\Users\Alif Ilham\.grok\sessions"
    r"\C%3A%5CUsers%5CAlif%20Ilham%5Csakina-launcher"
    r"\019f6103-b886-7133-8731-45b61f6ce643\images\1.jpg"
)
REPO = Path(r"C:\Users\Alif Ilham\Sakina-Launcher")
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"Source missing: {SRC}")

    im = Image.open(SRC).convert("RGBA")
    master = im.resize((512, 512), Image.Resampling.LANCZOS)

    brand = REPO / "docs" / "branding"
    assets = REPO / "Assets" / "branding"
    brand.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)

    master.save(brand / "ic_launcher.png", "PNG", optimize=True)
    master.save(assets / "ic_launcher.png", "PNG", optimize=True)
    im.convert("RGB").save(brand / "ic_launcher_source.jpg", "JPEG", quality=95)

    res = REPO / "app" / "src" / "main" / "res"
    for folder, size in SIZES.items():
        resized = master.resize((size, size), Image.Resampling.LANCZOS)
        out_dir = res / folder
        resized.save(out_dir / "ic_launcher.png", "PNG", optimize=True)
        resized.save(out_dir / "ic_launcher_round.png", "PNG", optimize=True)
        print(f"wrote {folder} {size}px")

    print("OK")


if __name__ == "__main__":
    main()
