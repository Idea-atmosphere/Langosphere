#!/usr/bin/env python3
"""
Download-count card (and its donate panel) for the README.

Reads the repository's releases from the GitHub API and writes, for English
and Persian × dark and light:

    downloads-<lang>-<theme>.svg   total downloads, release and star counts
    donate-<lang>-<theme>.svg      "support the project" panel, TON / TRX / ETH

plus a downloads.json snapshot. Standard library only — no third-party
packages — so it runs anywhere Python 3.8+ does.

    python3 scripts/downloads_card.py --repo Idea-atmosphere/Langosphere --out out/

The README places the two images side by side: the download card links to the
Releases page, the donate panel to the donation page (an <img> cannot carry
two links, hence two files). Neither has a per-release breakdown, so they stay
the same size however many releases there are.

The files are self-contained SVG: no scripts and no external references, so
GitHub's image proxy serves them unchanged. The Persian ones embed a small
subset of the Vazirmatn typeface (scripts/fonts/, SIL Open Font License) so
they look the same on every platform; the English ones use the system UI
font. Short SMIL animations fade the number in and pulse the heart; renderers
without SMIL simply show the finished image.
`.github/workflows/downloads-card.yml` runs this every hour and after every
release and pushes the result to the `stats` branch.

Environment: GITHUB_TOKEN (optional) raises the API rate limit from 60 to
5 000 requests an hour. The script only ever reads from the API.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from xml.sax.saxutils import escape

API = "https://api.github.com"

# ── palette (the app's own theme colours, see app/src/main/java/com/example/ui/theme/Color.kt) ──
PRIMARY = "#3A5AD4"
TERTIARY = "#7A4FD1"
SECONDARY = "#2F7D6B"
SECONDARY_LIGHT = "#5EC2A6"
THEMES = {
    "dark": dict(bg1="#0B0E15", bg2="#161C2C", stroke="#2A3350", text="#EEF1FF", muted="#9AA4C7",
                 grid="#263050", chip="#1B2237", chip_stroke="#2E3A5C", glow=PRIMARY, glow_op="0.35", glow2_op="0.16",
                 disc1="#5B79FF", disc2=TERTIARY, accent=SECONDARY_LIGHT,
                 num1="#AFC1FF", num2="#D9C7FF"),
    "light": dict(bg1="#FBFCFF", bg2="#EEF2FF", stroke="#D9DFF5", text="#101C4F", muted="#5B6690",
                  grid="#DDE3F5", chip="#FFFFFF", chip_stroke="#D9DFF5", glow=PRIMARY, glow_op="0.16", glow2_op="0.10",
                  disc1=PRIMARY, disc2=TERTIARY, accent=SECONDARY_LIGHT,
                  num1=PRIMARY, num2=TERTIARY),
}
FONT_EN = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Inter, Roboto, Ubuntu, 'Helvetica Neue', Arial, sans-serif"
FONT_FA = "Vazirmatn, Vazir, 'Segoe UI', 'Noto Sans Arabic', 'Noto Naskh Arabic', Tahoma, Arial, sans-serif"

FONT_FILE_FA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts", "Vazirmatn-subset.woff2")

W, H, PAD = 424, 172, 28          # download card
W_DONATE = 212                    # donate panel; 424 + 212 (+ the gap GitHub puts between images) ≈ README width
DONATE_URL = "https://idea-atmosphere.github.io/Nothing/designs/minimal.html"
COINS = (("TON", "#0098EA"), ("TRX", "#EA0029"), ("ETH", "#627EEA"))   # ticker, brand colour of the dot


# ── data ─────────────────────────────────────────────────────────────────────

def api_get(url: str, token: str | None):
    req = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "langosphere-downloads-card",
        "X-GitHub-Api-Version": "2022-11-28",
        **({"Authorization": f"Bearer {token}"} if token else {}),
    })
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp), resp.headers


def fetch(repo: str, token: str | None) -> dict:
    releases, page = [], 1
    while True:
        batch, _ = api_get(f"{API}/repos/{repo}/releases?per_page=100&page={page}", token)
        releases.extend(r for r in batch if not r.get("draft"))
        if len(batch) < 100:
            break
        page += 1
    releases.sort(key=lambda r: r.get("published_at") or r.get("created_at") or "")
    rows = [{
        "tag": r["tag_name"],
        "name": r.get("name") or r["tag_name"],
        "published": (r.get("published_at") or "")[:10],
        "prerelease": bool(r.get("prerelease")),
        "downloads": sum(a.get("download_count", 0) for a in r.get("assets", [])),
        "assets": [{"name": a["name"], "downloads": a.get("download_count", 0)} for a in r.get("assets", [])],
    } for r in releases]
    stars = None
    try:
        info, _ = api_get(f"{API}/repos/{repo}", token)
        stars = info.get("stargazers_count")
    except (urllib.error.URLError, json.JSONDecodeError):
        pass
    stable = [r for r in rows if not r["prerelease"]]
    return {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "repo": repo,
        "total": sum(r["downloads"] for r in rows),
        "stars": stars,
        "latest": (stable or rows or [None])[-1],
        "releases": rows,
    }


# ── text helpers ─────────────────────────────────────────────────────────────

FA_DIGITS = str.maketrans("0123456789,", "۰۱۲۳۴۵۶۷۸۹٬")


def num(n: int, lang: str) -> str:
    s = f"{n:,}"
    return s.translate(FA_DIGITS) if lang == "fa" else s


def plural(n: int, one: str, many: str) -> str:
    return one if n == 1 else many


JALALI_MONTHS = ("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                 "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")


def to_jalali(gy: int, gm: int, gd: int) -> tuple[int, int, int]:
    """Gregorian → Jalali (Solar Hijri), the arithmetic of jalaali-js / jdf."""
    g_days = (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    gy2 = gy - 1600
    gm2 = gm - 1
    gd2 = gd - 1
    g_day_no = 365 * gy2 + (gy2 + 3) // 4 - (gy2 + 99) // 100 + (gy2 + 399) // 400
    g_day_no += g_days[gm2] + gd2
    if gm2 > 1 and ((gy % 4 == 0 and gy % 100 != 0) or gy % 400 == 0):
        g_day_no += 1
    j_day_no = g_day_no - 79
    j_np = j_day_no // 12053
    j_day_no %= 12053
    jy = 979 + 33 * j_np + 4 * (j_day_no // 1461)
    j_day_no %= 1461
    if j_day_no >= 366:
        jy += (j_day_no - 1) // 365
        j_day_no = (j_day_no - 1) % 365
    if j_day_no < 186:
        jm, jd = 1 + j_day_no // 31, 1 + j_day_no % 31
    else:
        jm, jd = 7 + (j_day_no - 186) // 30, 1 + (j_day_no - 186) % 30
    return jy, jm, jd


def jalali_label(iso_date: str) -> str:
    y, m, d = (int(x) for x in iso_date.split("-"))
    jy, jm, jd = to_jalali(y, m, d)
    return f"{str(jd).translate(FA_DIGITS)} {JALALI_MONTHS[jm - 1]} {str(jy).translate(FA_DIGITS)}"


def strings(lang: str, d: dict) -> dict:
    n_rel = len(d["releases"])
    date = d["generated"][:10]
    stars = d.get("stars")
    if lang == "fa":
        return dict(
            title="مجموع دانلودها",
            sub=f"در {num(n_rel, lang)} انتشار" + (f" · {num(stars, lang)} ستاره" if stars is not None else ""),
            footer=f"به‌روزرسانی {jalali_label(date)} · هر ساعت",
            alt="شمار دانلودهای Langosphere",
            support="حمایت از پروژه",
            donate="حمایت مالی",
            donate_alt="حمایت از پروژه — Gram (TON)، Tron (TRX)، Ethereum (ETH)",
        )
    return dict(
        title="TOTAL DOWNLOADS",
        sub=f"across {num(n_rel, lang)} {plural(n_rel, 'release', 'releases')}"
            + (f" · ★ {num(stars, lang)} {plural(stars, 'star', 'stars')}" if stars is not None else ""),
        footer=f"Updated {date} · refreshed hourly",
        alt="Langosphere download counts",
        support="SUPPORT THE PROJECT",
        donate="Donate",
        donate_alt="Support the project — Gram (TON), Tron (TRX), Ethereum (ETH)",
    )


def text_width(s: str, size: float, lang: str) -> float:
    """Rough advance width for laying out chips (the real font is the viewer's)."""
    per = 0.46 if lang == "fa" else 0.58
    wide = sum(ch in "MW★" for ch in s)
    return (len(s) * per + wide * 0.35) * size


def font_face_css(path: str | None) -> str:
    """@font-face rule with the WOFF2 file inlined as a data: URI ('' if there is no file)."""
    if not path or not os.path.isfile(path):
        return ""
    with open(path, "rb") as f:
        data = base64.b64encode(f.read()).decode("ascii")
    return ("@font-face{font-family:'Vazirmatn';font-style:normal;font-weight:100 900;font-display:block;"
            f"src:url(data:font/woff2;base64,{data}) format('woff2');}}")


# ── drawing ──────────────────────────────────────────────────────────────────

def text(x, y, s, size, fill, lang, anchor="start", weight="500", extra="", children=""):
    """One <text>. For Persian the element is RTL: anchor 'start' sits on the RIGHT edge."""
    s = escape(s)
    font = FONT_FA if lang == "fa" else FONT_EN
    rtl = ' direction="rtl" unicode-bidi="embed"' if lang == "fa" else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-family="{font}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}"{rtl}{extra}>{children}{s}</text>')


def fade(delay: float = 0.0, hold: float = 0.0) -> str:
    """Opacity 0 → 1; `hold` seconds at 0 first. Static renderers ignore it."""
    dur = max(0.4, hold + 0.5)
    if hold:
        return (f'<animate attributeName="opacity" values="0;0;1" keyTimes="0;{hold / dur:.2f};1" '
                f'dur="{dur:.2f}s" begin="{delay:.2f}s" fill="freeze"/>')
    return f'<animate attributeName="opacity" values="0;1" dur="{dur:.2f}s" begin="{delay:.2f}s" fill="freeze"/>'


def download_glyph(cx: float, cy: float, t: dict) -> str:
    """Gradient disc with a download arrow; one soft ripple while the card appears."""
    r = 36
    arrow = (f'<path d="M{cx:.1f} {cy - 15:.1f} v22 M{cx - 9.5:.1f} {cy - 1:.1f} l9.5 9.5 9.5 -9.5 '
             f'M{cx - 14:.1f} {cy + 12:.1f} v5 h28 v-5" fill="none" stroke="#FFFFFF" stroke-width="3.4" '
             'stroke-linecap="round" stroke-linejoin="round"/>')
    return (f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r}" fill="none" stroke="{t["disc1"]}" stroke-width="2" opacity="0">'
            f'<animate attributeName="r" values="{r};{r + 20}" dur="2.2s" begin="0.4s" repeatCount="2"/>'
            f'<animate attributeName="opacity" values="0.5;0" dur="2.2s" begin="0.4s" repeatCount="2"/></circle>'
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r}" fill="url(#disc)"/>'
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r - 0.5}" fill="none" stroke="#FFFFFF" stroke-opacity="0.18"/>'
            f'<g opacity="0">{arrow}'
            '<animate attributeName="opacity" values="0;1" dur="0.5s" begin="0.15s" fill="freeze"/>'
            '<animateTransform attributeName="transform" type="translate" from="0 -8" to="0 0" '
            'dur="0.5s" begin="0.15s" fill="freeze"/></g>')


def defs(t: dict, w: int, style: str) -> str:
    return ('<defs>'
            f'<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{t["bg1"]}"/><stop offset="1" stop-color="{t["bg2"]}"/></linearGradient>'
            f'<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{t["glow"]}" stop-opacity="{t["glow_op"]}"/><stop offset="1" stop-color="{t["glow"]}" stop-opacity="0"/></radialGradient>'
            f'<radialGradient id="glow2" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{t["accent"]}" stop-opacity="{t["glow2_op"]}"/><stop offset="1" stop-color="{t["accent"]}" stop-opacity="0"/></radialGradient>'
            f'<linearGradient id="disc" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{t["disc1"]}"/><stop offset="1" stop-color="{t["disc2"]}"/></linearGradient>'
            f'<linearGradient id="num" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{t["num1"]}"/><stop offset="1" stop-color="{t["num2"]}"/></linearGradient>'
            f'<clipPath id="clip"><rect width="{w}" height="{H}" rx="20"/></clipPath>'
            f'{style}</defs>')


def body(t: dict, w: int, glow_cx: float, far_cx: float) -> str:
    """Rounded card with two soft glows: one behind the icon, one in the far bottom corner."""
    return (f'<rect x="0.5" y="0.5" width="{w - 1}" height="{H - 1}" rx="20" fill="url(#bg)" stroke="{t["stroke"]}"/>'
            f'<g clip-path="url(#clip)"><circle cx="{glow_cx:.0f}" cy="{H / 2:.0f}" r="170" fill="url(#glow)"/>'
            f'<circle cx="{far_cx:.0f}" cy="{H + 10}" r="150" fill="url(#glow2)"/></g>')


def card(d: dict, lang: str, theme: str, font_css: str = "") -> str:
    t = THEMES[theme]
    s = strings(lang, d)
    rtl = lang == "fa"
    style = f"<style>{font_css}</style>" if (rtl and font_css) else ""
    anchor = "start"  # right edge for rtl (direction="rtl"), left edge otherwise
    icon_cx = (W - PAD - 36) if rtl else (PAD + 36)
    col_x = (W - PAD - 96) if rtl else (PAD + 96)  # the text column's edge, next to the disc
    out = []
    a = out.append
    a(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t">')
    a(f'<title id="t">{escape(s["alt"])}</title>')
    a(defs(t, W, style))
    a(body(t, W, icon_cx, PAD + 40 if rtl else W - PAD - 40))
    a(download_glyph(icon_cx, H / 2, t))
    a(text(col_x, 56, s["title"], 12.5, t["muted"], lang, anchor, "700",
           extra=("" if rtl else ' letter-spacing="2.2"')))
    total = num(d["total"], lang)
    big = 52 if len(total) <= 5 else 44 if len(total) <= 7 else 36
    a(text(col_x, 104, total, big, "url(#num)", lang, anchor, "800", children=fade()))
    a(text(col_x, 129, s["sub"], 14.5, t["muted"], lang, anchor, "500"))
    a(text(col_x, 153, s["footer"], 11, t["muted"], lang, anchor, "500", extra=' opacity="0.9"'))
    a('</svg>')
    return "\n".join(out) + "\n"


HEART = ("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09"
         "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z")


def heart(cx: float, cy: float, size: float, fill: str, pulse: bool = False) -> str:
    """Material 'favorite' heart centred on (cx, cy), `size` px tall; optional two-beat pulse."""
    k = size / 24
    anim = ('<animateTransform attributeName="transform" type="scale" values="1;1.14;1;1.1;1" '
            'keyTimes="0;0.2;0.4;0.6;1" dur="1.4s" begin="0.4s" repeatCount="2"/>' if pulse else "")
    return (f'<g transform="translate({cx:.1f} {cy:.1f})"><g>{anim}'
            f'<path transform="translate({-12 * k:.2f} {-12 * k:.2f}) scale({k:.3f})" d="{HEART}" fill="{fill}"/></g></g>')


def donate_card(d: dict, lang: str, theme: str, font_css: str = "") -> str:
    t = THEMES[theme]
    s = strings(lang, d)
    rtl = lang == "fa"
    style = f"<style>{font_css}</style>" if (rtl and font_css) else ""
    w = W_DONATE
    cx = w / 2
    out = []
    a = out.append
    a(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{H}" viewBox="0 0 {w} {H}" role="img" aria-labelledby="t">')
    a(f'<title id="t">{escape(s["donate_alt"])}</title>')
    a(defs(t, w, style))
    a(body(t, w, cx, PAD if rtl else w - PAD))
    # heart in a gradient disc
    a(f'<circle cx="{cx}" cy="40" r="20" fill="url(#disc)"/>'
      f'<circle cx="{cx}" cy="40" r="19.5" fill="none" stroke="#FFFFFF" stroke-opacity="0.18"/>')
    a(heart(cx, 41, 20, "#FFFFFF", pulse=True))
    a(text(cx, 80, s["support"], 10.5, t["muted"], lang, "middle", "700",
           extra=("" if rtl else ' letter-spacing="1.6"')))
    # ticker chips with brand-coloured dots, centred as a row
    size = 10.5
    chips = [(name, col, text_width(name, size, "en") + 30) for name, col in COINS]
    gap = 6
    x = cx - (sum(c[2] for c in chips) + gap * (len(chips) - 1)) / 2
    for name, col, cw in chips:
        a(f'<rect x="{x:.1f}" y="92" width="{cw:.1f}" height="22" rx="11" fill="{t["chip"]}" stroke="{t["chip_stroke"]}"/>'
          f'<circle cx="{x + 12:.1f}" cy="103" r="3.2" fill="{col}"/>'
          + text(x + 20, 106.8, name, size, t["text"], "en", "start", "700"))
        x += cw + gap
    # the button: gradient pill with a small heart and the label
    bw, bh, by = 150, 34, 124
    bx = cx - bw / 2
    label_w = text_width(s["donate"], 13, lang)
    inner = 14 + 7 + label_w  # heart + gap + label, centred as one group
    a(f'<rect x="{bx:.1f}" y="{by}" width="{bw}" height="{bh}" rx="17" fill="url(#disc)"/>'
      f'<rect x="{bx + 0.5:.1f}" y="{by + 0.5}" width="{bw - 1}" height="{bh - 1}" rx="16.5" fill="none" stroke="#FFFFFF" stroke-opacity="0.22"/>')
    # the heart leads: left of the label in LTR, right of it in RTL (text() anchors Persian on its right edge)
    hx = cx + inner / 2 - 7 if rtl else cx - inner / 2 + 7
    lx = hx - 14 if rtl else hx + 14
    a(heart(hx, by + bh / 2 + 0.5, 14, "#FFFFFF"))
    a(text(lx, by + bh / 2 + 4.6, s["donate"], 13, "#FFFFFF", lang, "start", "700"))
    a('</svg>')
    return "\n".join(out) + "\n"


# ── main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--repo", required=True, help="OWNER/NAME")
    p.add_argument("--out", required=True, help="output directory")
    p.add_argument("--snapshot", help="use this downloads.json instead of calling the API (offline rendering)")
    p.add_argument("--font-fa", default=FONT_FILE_FA,
                   help="WOFF2 file embedded in the Persian cards (default scripts/fonts/Vazirmatn-subset.woff2)")
    p.add_argument("--no-embed-font", action="store_true", help="do not embed any font (smaller files, system fallback)")
    args = p.parse_args()
    font_css = "" if args.no_embed_font else font_face_css(args.font_fa)
    token = os.environ.get("GITHUB_TOKEN") or None
    if args.snapshot:
        with open(args.snapshot, encoding="utf-8") as f:
            d = json.load(f)
    else:
        try:
            d = fetch(args.repo, token)
        except urllib.error.HTTPError as e:
            print(f"GitHub API error {e.code} for {args.repo}: {e.reason}", file=sys.stderr)
            return 1
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "downloads.json"), "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")
    for lang in ("en", "fa"):
        for theme in ("dark", "light"):
            for name, fn in (("downloads", card), ("donate", donate_card)):
                path = os.path.join(args.out, f"{name}-{lang}-{theme}.svg")
                with open(path, "w", encoding="utf-8") as f:
                    f.write(fn(d, lang, theme, font_css))
    print(f"{d['total']} downloads over {len(d['releases'])} releases → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
