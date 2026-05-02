# Hammer Design System

The `Hd*` family of Composables in this directory is **the** vocabulary
for Hammer's redesigned screens. New screens compose these components;
they do not invent their own layouts. This document explains the
philosophy that ties them together and lists the building blocks that
already exist.

---

## Philosophy

Hammer is a tool for writers. The redesign treats it like a **printed
manuscript**, not a SaaS dashboard. The reference points are
typeset book pages, ledger paper, and editorial layouts — not Material
You's pillow-soft elevation or iOS's glassy cards.

That intent shows up in five rules. Every component in this directory
follows them; every new screen should too.

### 1. Hairlines, never elevation

Structure on a page comes from **rules** (1dp lines), not from drop
shadows or tonal elevation. We use Material 3's color tokens but
deliberately ignore its elevation system.

- Containers get a 1dp `outlineVariant` border, never a shadow.
- Surface tone changes (`surface` → `surfaceContainerLow` →
  `surfaceContainerHigh`) replace elevation when we need to separate
  a card from the page.
- Dividers are `Dp.Hairline`, not 1.dp. Compose draws hairline as a
  single device pixel, which matches the engraved feel.
- The `outline` token is reserved for the heavy "masthead" rule at the
  top of a section (see [`HdFolioDivider`](HdFolioDivider.kt)) **and**
  for hairline borders that sit on a saturated fill (see
  [`HdFab`](HdFab.kt)) where `outlineVariant` would disappear into the
  fill. `outlineVariant` is the body rule on neutral surfaces.
  Use both on purpose.

### 2. Square corners

The shape language is **rectangles**. No pill chips, no pebble FABs, no
12dp cards.

- Buttons, chips, FAB, picker cells, image drops, type stamps:
  `RectangleShape`.
- The only acceptable rounding is on bar-chart bars (2dp) — they need
  the cap to read as a bar, not a column of paint.
- This is what makes [`HdFab`](HdFab.kt),
  [`HdHairlineButton`](HdHairlineButton.kt),
  [`HdTagChip`](HdTagChip.kt) feel like one family.

### 3. Color is semantic, never chrome

The palette is **warm neutral** with one accent (`primary`) and a
fixed set of category colors for entry types. Color earns its keep —
it's never decoration.

- Chrome (nav rail, app bar, dividers, borders) is grayscale.
- `primary` is reserved for *the* primary action of a screen and the
  active-row accent on lists.
- Entry types (Person / Place / Thing / Event / Idea) own a stable
  color each, exposed via [`LocalHammerColors`](../theme/HammerExtendedColors.kt)
  (`colorFor(type)`). Those colors are the same in light and dark and
  must not be swapped at the screen level.
- Per-character tinting (timeline, attribution charts) uses
  `LocalHammerColors.colorForCharacter(name)` — a hashed slot from a
  shared palette, also stable across runs.

### 4. Editorial typography

Three faces, used for three jobs:

| Role    | Family                      | Usage                                                |
|---------|-----------------------------|------------------------------------------------------|
| Display | Roboto Flex (light weights) | Section titles, hero text, big stat numbers          |
| Body    | Roboto                      | Paragraphs, list rows, descriptions                  |
| Mono    | Roboto Mono                 | All-caps captions, counters, breadcrumbs, folio meta |

The mono is doing the most work. Anything that would be a "label",
"caption", or "metadata" in Material is a small-caps mono line in
Hammer — see [`HdMonoLabel`](HdMonoLabel.kt). It's the single most
recognizable signal of the design.

Section titles are introduced with a roman-numeral marker
(`§ I  Structure`) — see [`HdSectionHeader`](HdSectionHeader.kt) and
[`romanNumeral`](HdRomanNumeral.kt). Every section in a long-scrolling
screen gets one.

### 5. Color defaults from `MaterialTheme.colorScheme`

Components in this package read color directly from
`MaterialTheme.colorScheme.*` (or `LocalHammerColors` for category
tints). They do **not** default to `LocalContentColor.current`, because
nesting one component inside another would otherwise cause silent
color regressions when the parent didn't set the content color.

If you write a new `Hd*` component, follow the same rule.

---

## Goals

- **Cohesion.** A screen built from `Hd*` components should feel like
  the same app as every other screen, with no per-screen visual style
  to maintain.
- **Editorial calm.** Writers stare at the app for hours. The design
  should recede — no shadows, no decorative chrome.
- **Pleasing and Enticing** Animation is attractive and endeers the user is a plus.
  It should provide a sense of "flow" where possible.
- **Density without clutter.** Mono captions, hairline rules, and
  square corners pack a lot of information per square inch without
  feeling crowded. We lean into that for sidebars and dashboards.
- **Reusability over novelty.** Before adding a new screen-local
  composable, look here first. If the thing you need is 80% of an
  existing `Hd*`, extend the existing component rather than fork it.
- **Cross-platform parity.** Everything in this directory is
  `commonMain`. No Android-only, no Desktop-only.

---

## Component catalogue

Components are organized by what they're for. Every name starts with
`Hd` so they're trivially discoverable in IDE autocomplete.

### Structure & rhythm

These set up the page. Use them before reaching for raw `Column` /
`Row` / `Divider`.

- **[`HdSectionHeader`](HdSectionHeader.kt)** —
  `§ I  Title  ─────────  TRAILING META`. The default heading for any
  section in a scrolling screen. Pass an Int for auto-romanized
  section numbers.
- **[`HdHairlineSection`](HdHairlineSection.kt)** —
  `HdSectionHeader` + content + trailing hairline. The dashboard's
  section unit. `HdPlainSection` is the same minus the header for
  unmarked top strips.
- **[`HdFolioDivider`](HdFolioDivider.kt)** — the masthead rule stack
  (2dp `outline`, gap, 1dp `outlineVariant`) used directly under
  screen / modal headers. Anything that's a "title bar" should sit on
  one of these.
- **[`HdResponsiveStrip`](HdResponsiveStrip.kt)** — equally-weighted
  row on wide screens, vertical stack on narrow. The dashboard's
  go-to multi-column primitive. Children call `Modifier.cell()` to
  opt into the equal share.
- **[`HdHairlineGrid`](HdHairlineGrid.kt)** — N-column grid with
  hairlines between every cell. For paired numeric stats on narrow
  layouts where a single-column stack feels limp.

### Mono & numeric labels

The handwriting of the system. Reach for these instead of styling
`Text` by hand.

- **[`HdMonoLabel`](HdMonoLabel.kt)** — auto-uppercase mono caption.
  The default label primitive everywhere — counters, breadcrumbs,
  metadata, "FILTER ↗" affordances, footer folios.
- **[`HdMetadataItem`](HdMetadataItem.kt)** — stacked
  `LABEL` / `value` pair. For label-over-value blocks.
- **[`HdInlineStat`](HdStatBlock.kt)** — `Today  ·  847`, label
  left, value right. For tight metadata rows inside larger blocks.
- **[`HdStatBlock`](HdStatBlock.kt)** — the dashboard's foundational
  tile: mono label, big display number, optional subtitle, slot for
  progress bars / delta badges.
- **[`HdDeltaBadge`](HdDeltaBadge.kt)** — `▲ 22%  vs last week`.
  Sign of `percent` picks arrow + success/danger color from
  `LocalHammerColors`.
- **[`HdDailyGoalProgress`](HdDailyGoalProgress.kt)** — daily-goal
  pair with thin progress bar.

### Buttons & inputs

- **[`HdHairlineButton`](HdHairlineButton.kt)** — square-cornered
  hairline-bordered text button. Replaces `OutlinedButton` /
  `TextButton`. `emphasised = true` flips the border and label to
  `primary` for the "Save" half of a Save / Cancel pair.
- **[`HdFab`](HdFab.kt)** — square FAB, primary fill, hairline
  outline, no elevation. Replaces M3 `FloatingActionButton`.
- **[`HdHairlineField`](HdHairlineField.kt)** — labeled
  hairline-underline text field. Mono label + optional hint and
  counter, chrome-less `BasicTextField`, hairline rule below. The
  default text input.
- **[`HdHairlineTagField`](HdHairlineTagField.kt)** — same vocabulary
  but the value is a `List<String>` of `HdTagChip`s; Enter / comma
  adds a tag, Backspace on empty removes the last.
- **[`HdHairlineTypePicker`](HdHairlineTypePicker.kt)** — segmented
  picker for `EntryType`. Glyph square + mono label per cell, 2dp
  top stripe in the type's color when active.
- **[`HdSearchField`](HdSearchField.kt)** — 32dp tall hairline search
  bar with `⌕` glyph and clear `×`.
- **[`HdHairlineImageDrop`](HdHairlineImageDrop.kt)** — image-picker
  drop zone. Empty state has 135° stripes, dashed hairline border,
  centered `+` tile, mono hint, and a "browse files" affordance.
  Populated state shows the image with a hairline-bordered remove
  affordance.

### Categorization

The visual language for entry types — keep it consistent across every
screen that surfaces them.

- **[`EntryType.glyph()`](HdEntryGlyph.kt)** — single-character glyph
  per type (☉ ◇ ✦ ⚑ ✶) plus `HD_ALL_GLYPH = "∗"` for the unfiltered
  position.
- **[`HdCategorySwatch`](HdCategorySwatch.kt)** /
  **`HdCategoryChip`** — the colored square + mono name. Use for
  inline category indicators.
- **[`HdEntryFilterBar`](HdEntryFilterBar.kt)** — segmented
  hairline-bordered filter row with one cell per type
  (`☉ PEOPLE · 12`). The default filter UI for any list keyed by
  entry type.
- **[`HdTypeStamp`](HdTypeStamp.kt)** — postage-stamp affordance
  (colored glyph square + `TYPE` / `FILTER ↗`). Sits top-left on an
  entry card; tapping it re-runs the filter, not the card open.
- **[`HdTagChip`](HdTagChip.kt)** — `# label` chip with optional `×`
  remove. The tag primitive used inside cards and tag fields.
- **[`HdTypographicHero`](HdTypographicHero.kt)** — imageless hero
  zone for entry cards: 6dp accent stripe + low-opacity glyph
  backdrop + the entry name in display weight. Use when no cover
  image is set so the card never feels half-formed.
- **[`HdEngravingPlaceholder`](HdEngravingPlaceholder.kt)** — 45°
  hatched stripes + `[ENGRAVING · NAME]`. The "no image" placeholder
  inside detail layouts.

### Cards & charts

- **[`HdEntryCard`](HdEntryCard.kt)** — the encyclopedia entry card:
  hero (image or `HdTypographicHero`), `HdTypeStamp` overlay,
  optional title, description, optional `HdTagChip` row (wrapped or
  horizontally scrolling), hairline footer with mono meta + open
  affordance.
- **[`HdAttributionBar` / `HdMiniBarChart`](HdAttributionBar.kt)** —
  one-row character / category attribution bar (label, fill,
  trailing value); stacked into a mini bar chart.
- **[`HdBarChart`](HdBarChart.kt)** — flat vertical bar chart for
  per-chapter / per-scene tallies. No axes, no grid, no animation.
  For richer interactive charts, use KoalaPlot directly — this is
  the manuscript treatment.

### Navigation

- **[`HdNavRail`](HdNavRail.kt)** / **`HdNavRailItem`** — vertical
  rail with selected state driven by `secondaryContainer` so the
  per-project theme override colors the active item.
- **[`HdBottomBar`](HdBottomBar.kt)** / **`HdBottomBarItem`** — slim
  56dp icon-only bottom bar; the phone counterpart to `HdNavRail`.
  Replaces M3 `NavigationBar` because 80dp + gesture inset is too
  heavy for a writing app.

---

## Adding to the system

Before adding a new component:

1. **Reuse.** If a screen needs a layout that's 80% of an existing
   `Hd*`, extend or parameterize the existing component instead of
   forking it. Inlining a one-off layout into a screen is the thing
   we're trying to stop doing.
2. **Stay in vocabulary.** New components should be hairline +
   square-cornered, read color from `MaterialTheme.colorScheme.*` or
   `LocalHammerColors`, and prefer mono labels for metadata. If
   you're reaching for a shadow, a 12dp corner, or a non-semantic
   accent color, stop and ask first.
3. **Name it `Hd*`.** Same package, same prefix.
4. **Keep it `commonMain`.** Anything platform-specific belongs in
   the screen, not the component.
5. **Document the visual.** Each existing component has a KDoc with
   an ASCII sketch of what it looks like. Match that style — it
   pays off the next time someone is grepping for the right
   primitive.

When in doubt, the [components reference HTML in the design
bundle](../../../../../../../../docs) and the existing `Hd*` files are
the source of truth. Read them before reinventing.
