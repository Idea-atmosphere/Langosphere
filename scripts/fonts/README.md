# Fonts embedded in the download card

`Vazirmatn-subset.woff2` is a subset of **Vazirmatn** v33.003 by Saber
Rastikerdar (<https://github.com/rastikerdar/vazirmatn>), licensed under the
SIL Open Font License 1.1 — see `OFL.txt`. It keeps the variable weight axis
(100–900), basic Latin, the Persian/Arabic letters, both digit sets and the
punctuation the card uses: 342 glyphs, about 40 KB.

`scripts/downloads_card.py` inlines it (base64) in the Persian images so they
render the same everywhere; the English images use the system UI font and
embed nothing. The workflow never regenerates the subset — only the SVGs.

To rebuild it from the upstream variable font:

```sh
pip install fonttools brotli
pyftsubset "Vazirmatn[wght].ttf" \
  --unicodes="U+0020-007E,U+00A0,U+00B7,U+2605,U+200C,U+200F,U+060C,U+061B,U+061F,U+0621-063A,U+0640-064A,U+0660-066C,U+067E,U+0686,U+0698,U+06A9,U+06AF,U+06C0,U+06CC,U+06F0-06F9" \
  --layout-features="init,medi,fina,rlig,liga,calt,locl,kern,mark,mkmk,ccmp" \
  --flavor=woff2 --no-hinting \
  --output-file=Vazirmatn-subset.woff2
```
