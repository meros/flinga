#!/usr/bin/env python3
"""Flinga sales-pitch poster (bento grid, Apple-keynote style).

Every number on the poster is listed in FACTS below with its source; the footer prints the same list.
Run:  nix-shell -p 'python3.withPackages(ps: [ps.pillow])' --run \
        "python3 -I branding/pitch/make_pitch.py <inter.ttc> <shots dir> <icon.png> <out.png>"
"""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT, SHOTS, ICON, OUT = sys.argv[1:5]

# ---- facts (measured 2026-10-15/16, Chromecast with Google TV 2020, 2 GB RAM, Android 14) ----------------
FACTS = {
    "apk_flinga_B": 900_705,            # app-release.apk, build of main b5568b2
    "apk_official_B": 37_788_655,       # com.teliasonera.telia.playplus 9.23.2 base APK
    "pss_flinga_MB": 48.9,              # dumpsys meminfo TOTAL PSS while playing live
    "pss_official_MB": 338.0,           # dumpsys meminfo TOTAL PSS, official app home screen after 20 s
    "zap_median_ms": 578,               # key press to first decoded picture, 8 rounds (550-695 ms)
    "floppy_B": 1_474_560,              # 1.44 MB floppy
}
size_ratio = FACTS["apk_official_B"] / FACTS["apk_flinga_B"]          # 41.95
mem_ratio = FACTS["pss_official_MB"] / FACTS["pss_flinga_MB"]         # 6.9
assert round(size_ratio) == 42 and round(mem_ratio) == 7
assert FACTS["apk_flinga_B"] < FACTS["floppy_B"]

# ---- canvas ---------------------------------------------------------------------------------------------
W = 3200
M, G = 90, 36
BG = (11, 11, 16)
CARD = (20, 20, 28)
CARD2 = (27, 22, 38)
FG = (245, 243, 250)
MUTED = (165, 162, 180)
ACC = (194, 124, 245)
FILL = (153, 10, 230)
RAD = 52

def f(idx, size):
    return ImageFont.truetype(FONT, size, index=idx)

DISPLAY_XB, DISPLAY_B, DISPLAY_SB, TEXT_M, TEXT_R = 34, 32, 30, 10, 0

def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        t = (line + " " + word).strip()
        if draw.textlength(t, font=font) <= width:
            line = t
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines

def text_block(draw, xy, text, font, fill, width, lead=1.22):
    x, y = xy
    for ln in wrap(draw, text, font, width):
        draw.text((x, y), ln, font=font, fill=fill)
        y += int(font.size * lead)
    return y

def grad_text(img, xy, text, font, c1=(220, 160, 255), c2=(140, 20, 230)):
    """Text filled with a vertical purple gradient."""
    x, y = xy
    d = ImageDraw.Draw(img)
    l, t, r, b = d.textbbox((0, 0), text, font=font)
    w, h = r - l + 8, b - t + 8
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).text((-l + 4, -t + 4), text, font=font, fill=255)
    grad = Image.new("RGB", (w, h))
    gd = ImageDraw.Draw(grad)
    for i in range(h):
        k = i / max(1, h - 1)
        gd.line([(0, i), (w, i)], fill=tuple(int(c1[j] + (c2[j] - c1[j]) * k) for j in range(3)))
    img.paste(grad, (x + l - 4, y + t - 4), mask)
    return y + h

def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out

def card(img, box, fill=CARD, glow=None):
    x0, y0, x1, y1 = box
    tile = Image.new("RGB", (x1 - x0, y1 - y0), fill)
    if glow:
        g = Image.new("RGB", tile.size, fill)
        gd = ImageDraw.Draw(g)
        gx, gy, gr = glow
        gd.ellipse([gx - gr, gy - gr, gx + gr, gy + gr], fill=(110, 20, 180))
        g = g.filter(ImageFilter.GaussianBlur(gr // 2))
        tile = Image.blend(tile, g, 0.55)
    img.paste(rounded(tile, RAD), (x0, y0), rounded(tile, RAD))

def shot(name, crop=None):
    im = Image.open(f"{SHOTS}/{name}").convert("RGB")
    return im.crop(crop) if crop else im

def place(img, im, x, y, w, radius=28):
    h = round(im.size[1] * w / im.size[0])
    im = im.resize((w, h), Image.LANCZOS)
    img.paste(rounded(im, radius), (x, y), rounded(im, radius))
    return h

# ---- layout: compute heights first, then draw ------------------------------------------------------------
cw = W - 2 * M
y = M
R1H, R2H, R3H, R4H = 720, 960, 840, 440
FOOT = 245
H = M + R1H + G + R2H + G + R3H + G + R4H + G + FOOT + 40
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

# Row 1: hero + two stat tiles
hero_w = 1448
stat_w = (cw - hero_w - 2 * G) // 2
x_hero, x_s1, x_s2 = M, M + hero_w + G, M + hero_w + G + stat_w + G
card(img, (x_hero, y, x_hero + hero_w, y + R1H), CARD2, glow=(300, 420, 420))
icon = Image.open(ICON).convert("RGBA").resize((330, 330), Image.LANCZOS)
img.paste(icon, (x_hero + 70, y + 70), icon)
d.text((x_hero + 450, y + 40), "Flinga.", font=f(DISPLAY_XB, 270), fill=FG)
d.text((x_hero + 70, y + 440), "TV som hinner med.", font=f(DISPLAY_XB, 118), fill=FG)
text_block(d, (x_hero + 70, y + 610), "En oberoende klient för Telia Play, byggd för en Chromecast från 2020.",
           f(TEXT_M, 36), MUTED, hero_w - 140)

def stat(x, y0, big, label, detail, joke):
    card(img, (x, y0, x + stat_w, y0 + R1H), CARD)
    grad_text(img, (x + 56, y0 + 40), big, f(DISPLAY_XB, 300))
    d.text((x + 60, y0 + 400), label, font=f(DISPLAY_B, 84), fill=FG)
    yy = text_block(d, (x + 60, y0 + 520), detail, f(TEXT_M, 40), MUTED, stat_w - 120)
    d.text((x + 60, y0 + R1H - 80), joke, font=f(TEXT_M, 38), fill=ACC)

stat(x_s1, y, "42×", "mindre.", "0,9 MB. Originalappen väger 37,8 MB.", "Ryms på en diskett. Med marginal.")
stat(x_s2, y, "7×", "mindre minne.", "49 MB medan den spelar live. Originalappen: 338 MB.", "Resten av dina 2 GB får du behålla.")
y += R1H + G

# Row 2: three screenshot tiles
tw = (cw - 2 * G) // 3
shots2 = [
    (shot("02-channels.png", (0, 95, 940, 624)), "Riktiga loggor.",
     "I rätt färg, på mörkt och på ljust. Strular en logga får du kanalnamnet, inte ett hål."),
    (shot("81-series-detail.png"), "Serier som ser ut som serier.",
     "Affisch, säsonger och miniatyrer på en TV med 2 GB. Bilderna får högst 7 MB minne."),
    (shot("09-search-results.png", (0, 0, 1720, 967)), "Skriv utan mobilen.",
     "Eget tangentbord med å, ä och ö och förslag medan du skriver. 17 % färre tryck än QWERTY.*"),
]
for i, (im, head, sub) in enumerate(shots2):
    x = M + i * (tw + G)
    card(img, (x, y, x + tw, y + R2H), CARD)
    h = place(img, im, x + 24, y + 24, tw - 48)
    text_block(d, (x + 60, y + 24 + h + 40), head, f(DISPLAY_B, 70), FG, tw - 120, lead=1.1)
    text_block(d, (x + 60, y + 24 + h + 40 + 2 * 77 + 24), sub, f(TEXT_M, 40), MUTED, tw - 120)
y += R2H + G

# Row 3: wide overlay tile + two stat tiles
wide_w = 1448
card(img, (M, y, M + wide_w, y + R3H), CARD2, glow=(1200, 560, 380))
d.text((M + 70, y + 60), "Missade du början?", font=f(DISPLAY_XB, 100), fill=FG)
grad_text(img, (M + 70, y + 175), "Det gjorde du nyss.", f(DISPLAY_XB, 100))
text_block(d, (M + 70, y + 310), "Börja om programmet med ett tryck, rewind som bara är rewind, ingen extra meny.",
           f(TEXT_M, 40), MUTED, wide_w - 140)
ov = shot("213-overlay-rewind-bar.png", (0, 640, 1920, 1080))
place(img, ov, M + 24, y + R3H - 24 - round(440 * (wide_w - 48) / 1920), wide_w - 48)

def stat3(x, big, label, detail, joke):
    card(img, (x, y, x + stat_w, y + R3H), CARD)
    grad_text(img, (x + 56, y + 40), big, f(DISPLAY_XB, 280))
    yy = text_block(d, (x + 60, y + 370), label, f(DISPLAY_B, 70), FG, stat_w - 120, lead=1.1)
    text_block(d, (x + 60, yy + 20), detail, f(TEXT_M, 40), MUTED, stat_w - 120)
    d.text((x + 60, y + R3H - 80), joke, font=f(TEXT_M, 38), fill=ACC)

stat3(x_s1, "0,58", "sekunder från knapptryck till bild.", "Median av 8 kanalbyten.", "Fjärrkontrollen känns långsam.")
stat3(x_s2, "0", "saker som flyttar sig.", "61 tester och 165 skärmbilder vaktar layouten, även med förlängda titlar och fler siffror.", "Texten får vara hur lång som helst.")
y += R3H + G

# Row 4: three small tiles
def small(i, head, sub, icon_on=False):
    x = M + i * (tw + G)
    card(img, (x, y, x + tw, y + R4H), CARD)
    tx = x + 60
    if icon_on:
        ic = Image.open(ICON).convert("RGBA").resize((200, 200), Image.LANCZOS)
        img.paste(ic, (x + 60, y + 90), ic)
        tx = x + 300
    yy = text_block(d, (tx, y + 70), head, f(DISPLAY_B, 74), FG, tw - (tx - x) - 50, lead=1.12)
    text_block(d, (tx, yy + 24), sub, f(TEXT_M, 38), MUTED, tw - (tx - x) - 50)

small(0, "Programmet tar slut. Nästa börjar.", "I efterhand, av sig självt, på samma kanal.")
small(1, "Ikonen är en fraktal.", "En Koch-snöflinga ritad av kod, samma bild varje gång. Flinga är svenska för snöflinga.", icon_on=True)
small(2, "Byggd på en natt.", "Av AI-agenter, granskad av åtta granskare i fyra omgångar. Och sedan av dig.")
y += R4H + G

# Footer: sources + disclaimer
fs = f(TEXT_R, 30)
src = ("Mätt 15–16 okt 2026 på Chromecast med Google TV (2 GB RAM, Android 14, 32-bit). "
       "Storlek: app-release.apk 900 705 B mot originalets bas-APK 9.23.2 37 788 655 B (42,0×; diskett 1,44 MB = 1 474 560 B). "
       "Minne: dumpsys meminfo, TOTAL PSS 48,9 MB under live-uppspelning mot 338 MB på originalets startsida (6,9×; originalet med video är högre). "
       "Kanalbyte: median 578 ms, 550–695 ms, 8 mätningar, knapptryck till första avkodade bild; originalet ej mätt. "
       "Tester: 61 enhetstester och 165 skärmbilder, 0 geometriska skillnader vid förlängda titlar och max antal siffror. "
       "Granskning: 8 oberoende granskare, 4 omgångar. Skärmbilderna är renderade av Flingas egen testmiljö med inspelad data, inte fotograferade från TV:n. *Tangentbordsuppgift: SAS Blogs 2018-09-12, 3,32 mot 4,01 tryck per tecken (alfabetiskt 6-kolumnsgaller mot QWERTY).")
yy = text_block(d, (M, y + 20), src, fs, MUTED, cw)
d.text((M, yy + 14), "Flinga är en oberoende klient och inte från eller godkänd av Telia. Kanalloggor och programbilder tillhör respektive ägare.",
       font=f(TEXT_M, 30), fill=ACC)

img.save(OUT)
print("saved", OUT, img.size)
