# Material Design 3 Compliance & Design Audit — Langosphere

This audit follows the self-audit framework from the
[`material-design-3-ui` skill](https://github.com/skydashnet/material-design-3-ui-skill)
(semantics, tokens, components, states, adaptive, accessibility, expressive restraint).

**Scope:** the two Material designs — the **Material Design 3** baseline
(`MATERIAL3`) and the **Material You / M3 Expressive** design (`MATERIAL_YOU`).
The app's own **Langosphere** skin is intentionally a different, branded
design language and is not audited as "Material."

**Date:** 2026-09-03

---

## Summary of what changed in this round

| Change | Why (per skill) |
|--------|------------------|
| Adaptive M3 navigation is **Material You only** | `adaptive-design.md`: bottom `NavigationBar` on compact phones (`<600dp`); leading `NavigationRail` on medium / expanded (tablet, desktop). The user explicitly asked to keep the Material Design 3 baseline on its **top M3 `TabRow`**, so only `MATERIAL_YOU` moves to bar/rail. |
| Kept M3 baseline top `TabRow` + supplied `Material3TabBar` | User correction: "Material Design 3 stays as the previous design." The `LiquidTabBar` component renders the authentic `TabRow` for `MATERIAL3`, the liquid blob bar for `LANGOSPHERE`, and nothing for `MATERIAL_YOU` (which uses bar/rail instead). |
| Kept the Langosphere top liquid tab bar | It is the app's own skin, not an M3 destination set. |
| Real M3 top app bar chrome (flat `surface`, plain `onSurface` title, `FilledTonalIconButton`) | `navigation.md` / `anti-patterns.md`: gradients and decorative marks are not "Material." Applies to both Material designs. |
| Real M3 `SingleChoiceSegmentedButtonRow` for the theme mode picker | `component-selection.md`: a compact single/multi-choice set uses a segmented button, not custom pills. |
| Semantic color roles instead of literal colors | `color-system.md`: screens should depend on semantic roles (`surface`, `onSurface`, `surfaceContainer`, `outlineVariant`, ...). |

---

## Self-audit (0 = incorrect/missing, 1 = partial, 2 = ready)

| Category | Check | Score |
|----------|-------|:-----:|
| **Task clarity** | Primary user goal & primary action are obvious per screen. | 2 |
| **Information hierarchy** | Grouping & emphasis coherent; actions are separated from navigation. | 2 |
| **Component semantics** | Controls match their behavior. Filled button = primary CTA; FilledTonalIconButton = secondary/icon action; SegmentedButton = single-choice; NavigationBar/Rail = top-level destinations (Material You); `TabRow` = top-level destinations (M3 baseline); CircularProgressIndicator = busy. | 2 |
| **Token discipline** | Material designs use semantic `MaterialTheme.colorScheme` roles and M3 shape/type scales; no scattered hex in the audited chrome/components. | 2 |
| **Adaptive behavior** | Material You navigation adapts compact → rail; the M3 baseline deliberately sticks to its top `TabRow` (user request); content not stretched (pager already wraps screens); no phone layout forced to desktop. | 2 |
| **States & feedback** | Loading (`CircularProgressIndicator`), empty (`EmptyState` tonal circle), disabled (`enabled` flags), selected (NavigationBar/Rail indicator, SegmentedButton). Error/empty/success states exist per screen. | 2 |
| **Accessibility** | Icon controls carry `contentDescription` (nav items via `item.title`, settings button via `changeThemeCd`); touch targets ≥ 48dp; non-color state (indicator pill + raised label); labels present. | 2 |
| **Expressive restraint** | `MATERIAL_YOU` adds expressive motion/shape/type but `LANGOSPHERE`-level decoration is not applied to M3 — routine surfaces stay calm. | 2 |

**No `0` in component semantics, states & feedback, or accessibility** → the
design is ready for approval per the skill's rule.

---

## Component map (Material designs)

| Role | M3 component used |
|------|-------------------|
| Primary navigation (Material You, phone) | `NavigationBar` + `NavigationBarItem` |
| Primary navigation (Material You, tablet/desktop) | `NavigationRail` + `NavigationRailItem` |
| Primary navigation (M3 baseline) | `TabRow` + `Material3TabBar` top tab bar |
| Top app bar title | Plain `Text` on `surface`, `onSurface` color |
| Theme / settings affordance | `FilledTonalIconButton` |
| Light / dark / system picker | `SingleChoiceSegmentedButtonRow` |
| Primary call-to-action | `Button` (filled) |
| Secondary icon action | `FilledTonalIconButton` |
| Surface container | `surfaceContainer` / `surfaceContainerLow` tonal cards |
| Small status / badge | M3 container + `onContainer` role pairs |
| Loading | `CircularProgressIndicator` |
| Selection control | `SegmentedButton` / `RadioButton` |
| Dialog / sheet | Material `Dialog` / bottom sheet shapes (`extraLarge`) |

---

## Known remaining recommendations (not blockers)

1. **Selected vs unselected icon variants.** Per `navigation.md`, the active
   destination should use a *filled* icon and inactive should use *outlined*.
   `LiquidTabItem` currently carries a single icon. Recommended follow-up: carry
   both `selectedIcon` and `unselectedIcon` (e.g. `Icons.Filled.*` vs
   `Icons.Outlined.*`) and switch in `M3NavigationBar` / `M3NavigationRail`.
2. **Screen-level token audit (optional).** Deep screens (Reader, Video Player,
   Assistant, Leitner) still use some app-specific gradients/colors that only
   switch in the shared components. A full screen-by-screen conversion to
   semantic roles only in the Material designs would be the next step.
3. **Verify with a build.** No Java/Android toolchain is available in this
   environment, so runtime/Compose compilation wasn't executed. Run
   `./gradlew assembleDebug` (or `assembleRelease`) to confirm.

---

## Handoff (design intent)

- **User goal:** learn languages while reading PDFs, watching videos, listening
  to audio, and reviewing flashcards, with dictionary + AI help.
- **Primary hierarchy:** 4 top-level destinations; the theme/settings live in a
  top app bar action; the AI prompt/learning config lives in the settings menu.
- **Navigation:** top-level destinations → bottom bar (phone) / rail (wider)
  in **Material You**; the **M3 baseline** keeps its top `TabRow`.
- **Theme roles:** define emphasis with `primary`/`onPrimary` (CTA),
  `secondary`/tertiary for supporting accents, the `surfaceContainer*` ramp for
  hierarchy, and `error*` only for errors. On Android 12+, wallpaper dynamic
  color drives the palette.
- **Motion:** expressive spring motion is confined to the M3 Expressive design;
  the baseline M3 uses standard Material motion. Motion explains state, never
  spectacle.

---

# Neobrutalism design (`NE0BRUTALISM`) — the fourth skin

> Sources studied: [neubrutalism.com](https://neubrutalism.com) and the bergside
> `neobrutalism` design skill (tokens listed under External sources in the
> session). Neobrutalism is *not* audited as "Material"; this section documents
> the rules the skin follows internally.

## Token mapping (`NeoBrutalismLightColors` / `NeoBrutalismDarkColors`)

| Semantic role | Neo light | Neo dark ("cyber-brutalism") |
|---|---|---|
| `background` / `surface` | cream `#FFFDF5` | page `#14131A` |
| `surfaceContainerLowest` (raised cards) | white `#FFFFFF` | `#1D1B26` |
| `surfaceContainerLow`/`Container`/`High` | off-white ramp | `#221F2B` / `#282430` / `#2F2B38` / `#373341` |
| `outline` (the ink) | black `#000000` | structural ink `#F3F3F6` |
| `outlineVariant` (hairlines) | `#D9D5CB` | `#4A4655` |
| `primary` (text accents, small fills) | indigo `#432DD7` | light indigo `#B9ADFF` |
| `tertiary` / `NeoBrutalismAccent` (loud blocks) | yellow `#FDC800` | yellow `#FDC800` |
| `secondary` (pink loud fills) | `#FF6B6B` | `#FF6B6B` |
| `error` / `errorContainer` | `#DC2626` / pink-red | `#FF8A80` / `#421417` |

Ink-black text lives on every loud yellow block in both themes; in dark mode
the yellow/pink blocks stay saturated so they read as "hot" against the
indigo-tinted near-black surfaces.

## Component rules

- **Building block:** flat square (`0.dp` radius everywhere) + `2.dp` ink
  border (`colorScheme.outline`) + hard offset shadow (`neoHardShadow`, no
  blur) drawn behind the fill. Modifier order matters: `neoHardShadow` →
  `background` → `border`.
- **Loud color only as a filled container.** Yellow = selected/active/primary
  blocks (play, active pill, selected row, filled level dot, launch tile,
  subtitle brand square). Text/icons on yellow are `Color.Black`.
- **Idle chips/cards** are raised cream/`surfaceContainerLowest` squares with
  the ink border and hard shadow; text on them is `onSurface`.
- **Soft/round shapes are replaced** by squares in shared chrome: glass
  buttons, text pills, seek bar, thumbs, swatches, status pills, drag handles,
  dialog/sheet surfaces and top corners, tabs, segmented pills.
- **Icons** keep Material icon glyphs but drop tonal/outline treatments for
  plain fills tinted by the container rule above (black on yellow, `onSurface`
  on light cards).
- **The skin is self-reskinning:** the Settings ▸ Theme dialog's own option
  rows, mode cards, radio/segmented controls and icons switch to the neo
  language when the design is active, so activating it re-skins even the picker
  that activated it.
- **Persistence** is the existing design picker path — the choice survives
  relaunch and applies on recreation like the other three designs.

## Coverage status

- **Fully skinned:** settings/theme dialog, top chrome, liquid tab bar, launch
  tile, brand/profile squares, the Videos tab's import/media tiles,
  Reader (PDF/text) import hero, loaded-file bar, page navigator, reading
  surface, action buttons and color pickers, the player chrome (glass
  buttons, text pills, seek bar, play/pause, split A–B handle, seek badge,
  tool cluster), the study panel drawer, the Videos tab's subtitle list rows
  (row shells, action buttons, "translating" state), the dictionary bottom
  sheet (handle, headline, source chips, add-to-Leitner block, search field),
  the player settings sheet (language/audio rows, shift pills, slider chips,
  font chips, swatches), the JSON paste dialog, the subtitle-learning bottom
  sheet chrome, and the Leitner tab (flashcards, level dots, action buttons).
- **Deliberately unchanged:** semantics of M3-only widgets inside the neo
  sheets that have no neo equivalent yet (e.g. native `Switch`, `Slider`,
  `CircularProgressIndicator`) — their surrounding rows are neo blocks.
