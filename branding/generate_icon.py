#!/usr/bin/env python3
"""Deterministic icon, banner and Android resource generator for the app ("Flinga").

The mark is a Koch snowflake ("flinga" is Swedish for snowflake) with a play triangle cut
out of its centre. The flake is drawn as nested growth rings, one per Koch iteration, so the
algorithm that builds it stays visible: iteration 0 is the triangle, 1 the hexagram, 4 the flake.

Run (NixOS; the python3 + package form below is the one that puts numpy and Pillow on the path):

  FONT=$(nix build --no-link --print-out-paths nixpkgs#inter)/share/fonts/truetype/Inter.ttc
  nix-shell -p 'python3.withPackages(ps: [ps.numpy ps.pillow])' \
      --run "python3 -I branding/generate_icon.py --font $FONT branding/out branding/android-res"

Output is byte-for-byte reproducible for a given numpy, Pillow and FreeType build (checked with
numpy 2.5.2, Pillow 12.3.0, FreeType 2.14.3, Inter 4.1). No randomness except the seeded attractor.
"""
import argparse
import glob
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- parameters (tweak here)
# Colours: client/design/DESIGN.md tokens.
BG = (0x0B, 0x0B, 0x10)          # BG
SURFACE = (0x1C, 0x1C, 0x25)     # SURFACE
ACCENT = (0x99, 0x0A, 0xE6)      # ACCENT
ACCENT_ON_DARK = (0xC2, 0x7C, 0xF5)  # ACCENT_ON_DARK
TEXT = (0xF5, 0xF5, 0xF7)        # TEXT
TEXT_2 = (0xB0, 0xAF, 0xBA)      # TEXT_2

# Final mark: Koch snowflake with a play cut-out.
KOCH_DEPTH = 4            # Koch iterations of the outline (3 still reads; 5 is invisible below 512 px)
KOCH_ROTATION = 0.0       # degrees; 0 puts a flake point at 3 o'clock, aligned with the play tip
RING_TONES = True         # draw each iteration as a ring, lighter towards the centre
RING_STEP = 0.55          # how much lighter the innermost ring is than the outermost (0..1)
GRADIENT_ANGLE = -45.0    # degrees; light comes from the top right
PLAY_SCALE = 0.45         # play cut-out circumradius as a fraction of the flake radius
PLAY_OFFSET = 0.08        # optical centring: shift the play triangle right by this x radius
SUPERSAMPLE = 4           # render at 4x, downsample with Lanczos

# Names for the banners (primary first) and the font face (Inter.ttc index 30 = Inter Display SemiBold).
NAMES = ["Flinga", "Flimmer"]
FONT_INDEX = 30

# Direction previews (exploration; not used by the final mark).
JULIA_C = complex(-0.8, 0.156)
JULIA_SPAN = 3.2
JULIA_ITER = 300
DEJONG = dict(a=-2.24, b=0.43, c=-0.65, d=-2.43)
DEJONG_POINTS = 200_000
DEJONG_STEPS = 60
DEJONG_SEED = 7
SIERPINSKI_DEPTH = 3

# Android sizes.
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_ZONE = 66 / 108      # adaptive icon safe zone diameter as a fraction of the layer

# ---------------------------------------------------------------- helpers


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, format="PNG", optimize=False, compress_level=9)


def ramp(t):
    """0..1 -> BG -> ACCENT -> ACCENT_ON_DARK -> TEXT."""
    stops = np.array([BG, ACCENT, ACCENT_ON_DARK, TEXT], float)
    pos = np.array([0, 0.45, 0.8, 1.0])
    t = np.clip(t, 0, 1)
    out = np.empty(t.shape + (3,))
    for k in range(3):
        out[..., k] = np.interp(t, pos, stops[:, k])
    return out.round().astype(np.uint8)


def mix(c0, c1, t):
    return tuple(int(round(a * (1 - t) + b * t)) for a, b in zip(c0, c1))


def gradient(w, h, c0, c1, angle=GRADIENT_ANGLE, t_lo=0.0, t_hi=1.0):
    y, x = np.mgrid[0:h, 0:w]
    s = max(w, h)
    a = np.radians(angle)
    t = ((x - w / 2) * np.cos(a) + (y - h / 2) * np.sin(a)) / (0.7 * s) + 0.5
    t = (t_lo + np.clip(t, 0, 1) * (t_hi - t_lo))[..., None]
    return Image.fromarray((np.array(c0) * (1 - t) + np.array(c1) * t).round().astype(np.uint8))


def koch_polygon(cx, cy, r, depth, rot=KOCH_ROTATION):
    """Koch snowflake outline: start from a triangle, replace each edge by 4 edges, outward bump."""
    pts = [np.array([cx + r * np.cos(a), cy + r * np.sin(a)])
           for a in np.radians([rot, rot + 120, rot + 240])]
    pts = pts + [pts[0]]
    c60, s60 = 0.5, np.sqrt(3) / 2
    for _ in range(depth):
        new = []
        for p, q in zip(pts[:-1], pts[1:]):
            v = (q - p) / 3
            bump = np.array([v[0] * c60 + v[1] * s60, -v[0] * s60 + v[1] * c60])
            new += [p, p + v, p + v + bump, p + 2 * v]
        pts = new + [pts[-1]]
    return [tuple(p) for p in pts[:-1]]


def play_triangle(cx, cy, r):
    pr = PLAY_SCALE * r
    return [(cx + pr * np.cos(a) + pr * PLAY_OFFSET, cy + pr * np.sin(a))
            for a in np.radians([0, 120, 240])]


# ---------------------------------------------------------------- the mark


def mark_rgba(size, radius_frac, mono=None):
    """The flake on a transparent layer. radius_frac: flake circumradius / size.
    mono: an RGB colour to draw everything in one colour (themed / monochrome icon)."""
    N = size * SUPERSAMPLE
    cx = cy = N / 2
    r = radius_frac * N
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    if mono is not None:
        fill = Image.new("RGB", (N, N), mono)
        mask = Image.new("L", (N, N), 0)
        ImageDraw.Draw(mask).polygon(koch_polygon(cx, cy, r, KOCH_DEPTH), fill=255)
        layer.paste(fill, (0, 0), mask)
    else:
        depths = range(KOCH_DEPTH, -1, -1) if RING_TONES else [KOCH_DEPTH]
        for d in depths:
            # outer ring (d = KOCH_DEPTH) darkest; inner triangle (d = 0) lightest
            k = 0 if not RING_TONES else (KOCH_DEPTH - d) / KOCH_DEPTH
            fill = gradient(N, N, ACCENT, ACCENT_ON_DARK, t_lo=RING_STEP * k, t_hi=0.70 + 0.30 * k)
            mask = Image.new("L", (N, N), 0)
            ImageDraw.Draw(mask).polygon(koch_polygon(cx, cy, r, d), fill=255)
            layer.paste(fill, (0, 0), mask)
    cut = Image.new("L", (N, N), 0)
    ImageDraw.Draw(cut).polygon(play_triangle(cx, cy, r), fill=255)
    layer.paste((0, 0, 0, 0), (0, 0), cut)
    return layer.resize((size, size), Image.LANCZOS)


def icon_square(size, rounded=True):
    """Full app icon: BG tile (rounded) with the mark."""
    N = size * SUPERSAMPLE
    tile = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    m = Image.new("L", (N, N), 0)
    if rounded:
        ImageDraw.Draw(m).rounded_rectangle([0, 0, N - 1, N - 1], radius=int(0.22 * N), fill=255)
    else:
        m.paste(255, (0, 0, N, N))
    tile.paste(BG + (255,), (0, 0), m)
    tile = tile.resize((size, size), Image.LANCZOS)
    mark = mark_rgba(size, 0.40)
    return Image.alpha_composite(tile, mark)


# ---------------------------------------------------------------- exploration directions


def direction_julia(n):
    y, x = np.mgrid[0:n, 0:n]
    z = (x / n - 0.5) * JULIA_SPAN + 1j * (y / n - 0.5) * JULIA_SPAN
    mu = np.zeros(z.shape)
    alive = np.ones(z.shape, bool)
    for i in range(JULIA_ITER):
        z[alive] = z[alive] ** 2 + JULIA_C
        esc = alive & (np.abs(z) > 4)
        mu[esc] = i + 1 - np.log(np.log(np.abs(z[esc]))) / np.log(2)  # smooth colouring
        alive &= ~esc
    return Image.fromarray(ramp(np.where(alive, 0, np.sqrt(mu / JULIA_ITER * 6))))


def direction_dejong(n):
    rng = np.random.default_rng(DEJONG_SEED)
    a, b, c, d = DEJONG["a"], DEJONG["b"], DEJONG["c"], DEJONG["d"]
    x, y = rng.uniform(-1, 1, DEJONG_POINTS), rng.uniform(-1, 1, DEJONG_POINTS)
    H = np.zeros((n, n))
    for s in range(DEJONG_STEPS):
        x, y = np.sin(a * y) - np.cos(b * x), np.sin(c * x) - np.cos(d * y)
        if s > 10:
            ix = ((x + 2.2) / 4.4 * n).astype(int).clip(0, n - 1)
            iy = ((y + 2.2) / 4.4 * n).astype(int).clip(0, n - 1)
            np.add.at(H, (iy, ix), 1)
    t = np.log1p(H) / np.log1p(H).max()
    return Image.fromarray(ramp(t ** 0.8))


def direction_sierpinski(n):
    N = n * SUPERSAMPLE
    mask = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(mask)
    h = 0.66 * N
    s = h * 2 / np.sqrt(3)
    cx = N * 0.5 + h / 6
    A, B, C = (cx - h * 2 / 3, N / 2 - s / 2), (cx - h * 2 / 3, N / 2 + s / 2), (cx + h / 3, N / 2)

    def rec(a, b, c, k):
        if k == 0:
            g = np.mean([a, b, c], axis=0)
            d.polygon([tuple(g + (np.array(p) - g) * 0.92) for p in (a, b, c)], fill=255)
            return
        ab, bc, ca = [tuple((np.array(p) + q) / 2) for p, q in ((a, b), (b, c), (c, a))]
        rec(a, ab, ca, k - 1)
        rec(ab, b, bc, k - 1)
        rec(ca, bc, c, k - 1)

    rec(A, B, C, SIERPINSKI_DEPTH)
    im = Image.new("RGB", (N, N), BG)
    im.paste(gradient(N, N, ACCENT, ACCENT_ON_DARK), (0, 0), mask)
    return im.resize((n, n), Image.LANCZOS)


def direction_koch(n):
    im = Image.new("RGBA", (n, n), BG + (255,))
    return Image.alpha_composite(im, mark_rgba(n, 0.40)).convert("RGB")


# ---------------------------------------------------------------- banner


def banner(w, h, name, font_path):
    N = SUPERSAMPLE
    W, H = w * N, h * N
    im = Image.new("RGBA", (W, H), BG + (255,))
    # Decorative: nested Koch flake outlines (depths 1..5) bleeding off the right edge, faint.
    d = ImageDraw.Draw(im)
    for depth in range(1, 6):
        poly = koch_polygon(W * 1.06, H * 0.5, H * (0.35 + 0.13 * depth), depth)
        d.line(poly + [poly[0]], fill=mix(BG, ACCENT, 0.10 + 0.04 * depth) + (255,),
               width=int(round(1.25 * N * h / 360)), joint="curve")
    im = im.resize((w, h), Image.LANCZOS)
    # Mark, left; then the wordmark.
    ms = int(round(h * 0.56))
    mark = mark_rgba(ms, 0.48)
    x0 = int(round(w * 0.07))
    im.alpha_composite(mark, (x0, (h - ms) // 2))
    font = ImageFont.truetype(font_path, int(round(h * 0.21)), index=FONT_INDEX)
    d = ImageDraw.Draw(im)
    bbox = d.textbbox((0, 0), name, font=font, anchor="ls")
    tx = x0 + ms + int(round(h * 0.06))
    ty = h // 2 + (-bbox[1]) // 2  # centre the cap height on the mark
    d.text((tx, ty), name, font=font, fill=TEXT, anchor="ls")
    return im.convert("RGB")


# ---------------------------------------------------------------- outputs


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""

COLOR_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- DESIGN.md BG token -->
    <color name="ic_launcher_background">#0B0B10</color>
</resources>
"""


def label(im, text, font_path, size=16):
    """Image with a caption strip under it."""
    font = ImageFont.truetype(font_path, size, index=10)
    out = Image.new("RGB", (im.width, im.height + size + 12), BG)
    out.paste(im.convert("RGB"), (0, 0))
    ImageDraw.Draw(out).text((4, im.height + 4), text, font=font, fill=TEXT_2)
    return out


def on_bg(im, color=SURFACE):
    base = Image.new("RGBA", im.size, color + (255,))
    return Image.alpha_composite(base, im.convert("RGBA")).convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("outdir")
    ap.add_argument("resdir", nargs="?")
    ap.add_argument("--font", help="Inter.ttc (nix build nixpkgs#inter)")
    args = ap.parse_args()
    font = args.font or sorted(glob.glob("/nix/store/*-inter-4.1/share/fonts/truetype/Inter.ttc"))[0]
    out = args.outdir
    res = args.resdir or os.path.join(out, "..", "android-res")

    # 1. Direction previews (256 px) and their 48 px tests.
    dirs = [("julia", direction_julia), ("dejong-attractor", direction_dejong),
            ("sierpinski-play", direction_sierpinski), ("koch-flake-play", direction_koch)]
    previews = []
    for key, f in dirs:
        im = f(256)
        save(im, f"{out}/direction_{key}.png")
        previews.append((key, im))

    # 2. Final icons.
    icon512 = icon_square(512)
    icon192 = icon_square(192)
    icon48 = icon_square(48)
    save(icon512, f"{out}/icon_512.png")
    save(icon192, f"{out}/icon_192.png")
    save(icon48, f"{out}/icon_48.png")
    # Legibility: 48 px (rendered direct, and 512 downscaled) shown 4x with nearest-neighbour.
    down = icon512.resize((48, 48), Image.LANCZOS)
    test = Image.new("RGB", (48 * 4 * 2 + 16, 48 * 4), SURFACE)
    test.paste(on_bg(icon48).resize((192, 192), Image.NEAREST), (0, 0))
    test.paste(on_bg(down).resize((192, 192), Image.NEAREST), (192 + 16, 0))
    save(test, f"{out}/legibility_48.png")

    fg432 = mark_rgba(432, 0.98 * SAFE_ZONE / 2)  # flake inside the 66 % safe circle
    mono432 = mark_rgba(432, 0.98 * SAFE_ZONE / 2, mono=(255, 255, 255))
    save(fg432, f"{out}/adaptive_foreground_432.png")
    save(mono432, f"{out}/adaptive_monochrome_432.png")

    # 3. Banners: primary name and the runner-up, at 640x360 (xhdpi) and 1280x720.
    banners = {}
    for name in NAMES:
        for w, h in ((640, 360), (1280, 720)):
            b = banner(w, h, name, font)
            save(b, f"{out}/banner_{name.lower()}_{w}x{h}.png")
            banners[(name, w)] = b

    # 4. Android resources mirroring res/.
    for dens, s in LEGACY_SIZES.items():
        save(icon_square(s), f"{res}/mipmap-{dens}/ic_launcher.png")
    for dens, s in FOREGROUND_SIZES.items():
        save(mark_rgba(s, 0.98 * SAFE_ZONE / 2), f"{res}/mipmap-{dens}/ic_launcher_foreground.png")
        save(mark_rgba(s, 0.98 * SAFE_ZONE / 2, mono=(255, 255, 255)),
             f"{res}/mipmap-{dens}/ic_launcher_monochrome.png")
    os.makedirs(f"{res}/mipmap-anydpi-v26", exist_ok=True)
    with open(f"{res}/mipmap-anydpi-v26/ic_launcher.xml", "w") as fh:
        fh.write(ADAPTIVE_XML)
    os.makedirs(f"{res}/values", exist_ok=True)
    with open(f"{res}/values/ic_launcher_background.xml", "w") as fh:
        fh.write(COLOR_XML)
    save(banners[(NAMES[0], 640)], f"{res}/drawable-xhdpi/banner.png")

    # 5. Contact sheet.
    adaptive_preview = Image.new("RGBA", (432, 432), BG + (255,))
    adaptive_preview.alpha_composite(fg432)
    circle = Image.new("L", (432 * 4, 432 * 4), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, 432 * 4 - 1, 432 * 4 - 1], fill=255)
    circle = circle.resize((432, 432), Image.LANCZOS)
    masked = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    masked.paste(adaptive_preview, (0, 0), circle)
    safe = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    sr = SAFE_ZONE * 432 / 2
    ImageDraw.Draw(safe).ellipse([216 - sr, 216 - sr, 216 + sr, 216 + sr], outline=TEXT_2 + (255,), width=1)
    masked.alpha_composite(safe)

    row1 = [label(im, f"direction: {k}", font) for k, im in previews]
    row2 = [label(on_bg(icon512).resize((256, 256), Image.LANCZOS), "icon 512 (shown 256)", font),
            label(on_bg(icon192), "icon 192", font),
            label(on_bg(masked).resize((256, 256), Image.LANCZOS), "adaptive, circle mask + safe zone", font),
            label(on_bg(mono432).resize((256, 256), Image.LANCZOS), "monochrome layer", font),
            label(test, "48 px: direct | 512 downscaled (4x)", font)]
    row3 = [label(banners[(n, 640)], f"banner {n} 640x360", font) for n in NAMES]
    rows = [row1, row2, row3]
    pad = 24
    W = max(sum(i.width for i in r) + pad * (len(r) + 1) for r in rows)
    H = sum(max(i.height for i in r) for r in rows) + pad * (len(rows) + 1)
    sheet = Image.new("RGB", (W, H), BG)
    y = pad
    for r in rows:
        x = pad
        for i in r:
            sheet.paste(i, (x, y))
            x += i.width + pad
        y += max(i.height for i in r) + pad
    save(sheet, f"{out}/contact_sheet.png")


if __name__ == "__main__":
    main()
