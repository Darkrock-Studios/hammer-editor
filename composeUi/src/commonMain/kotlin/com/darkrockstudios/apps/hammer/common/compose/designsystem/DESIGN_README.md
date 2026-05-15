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
- A vertical hairline along the **left edge** of a section is the
  Hammer way to group content. The rule runs the full height of the
  section — header through content — like a marginalia bracket on a
  manuscript page, and the content is inset (`16.dp`) to clear the
  rule. This substitutes for the visual grouping that elevation,
  card backgrounds, or trailing dividers would normally provide.
  See [`HdHairlineSection`](HdHairlineSection.kt). When you need to
  signal "these things belong together" on a long-scrolling screen,
  reach for the marginalia rule before any heavier chrome.

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
| Mono    | IBM Plex Mono (bundled)     | All-caps captions, counters, breadcrumbs, folio meta |

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

## Greebles

A **greeble** is a small, catalogue-card-feeling label that adds
typeset texture without doing structural work — entity IDs
(`ENT-034`), inline counters (`256 W`, `9 ENTRIES`), section markers
(`§ I`), folio captions, masthead stamps. They're the small-caps
mono lines that make a page feel set in lead rather than rendered
from a template, and they're a big part of why the design system
works. We want more of them, not fewer.

The rule is **a greeble has to mean something**. It has to encode a
real value the user could in principle verify, even if they never
would. `ENT-034` is the entry's actual id. `256 W` is the actual
word count. `§ I  Structure` numbers the actual first section of
the screen. The shape is decorative; the content is not.

What we don't ship: fabricated bookends — `FOL. 01 OF 14`,
`VOL. I`, `EDITION VII` — that look right but have no source of
truth behind them. A reader who looks twice feels lied to, and the
catalogue-card credibility of every other greeble drops with it.

When the obvious source isn't there, **find a less obvious real
one** before reaching for filler. Examples that have anchored
real greebles in this codebase: section counts (`§§ 3`), entity
counts (`9 ENTRIES`), last-edit timestamps, locale codes, format
versions, dictionary status, sync state, on/off toggles
(`ON · 1,000 W / WEEK`). A faint real number reads better than a
confident invented one. If after looking you genuinely can't find
something real for the slot — leave the slot empty rather than
fabricate.

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
  `HdSectionHeader` + content with a left-side marginalia rule running
  the full section height. The default section unit on long-scrolling
  screens — the rule is what visually groups header + content together
  in lieu of elevation or card backgrounds. `HdPlainSection` is the
  same minus the header and the rule, for unmarked top strips.
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
- **[`HdCatalogueCard`](HdCatalogueCard.kt)** — doubled-hairline
  library-card affordance for highlighting one section above the rest.
  Outer + inner hairline borders separated by a 6dp gap, with up to
  four corner greeble slots (`topStart` / `topEnd` / `bottomStart` /
  `bottomEnd`) that "punch through" the inner border with a
  `surface`-colored background. Use *sparingly* — its job is to make
  one group louder than the marginalia rules around it. Greebles
  follow the "must mean something" rule; leave a slot null rather
  than fabricate.

      ┌──────────────────────────────────────────────────┐
      │ ┌──[ § III · SYNC ]────────────[ CONNECTED ]──┐  │
      │ │   <body content>                             │  │
      │ └─[ KTOR · HTTPS ]─────────[ LAST SYNC 14:32 ]┘  │
      └──────────────────────────────────────────────────┘

### Mono & numeric labels

The handwriting of the system. Reach for these instead of styling
`Text` by hand.

- **[`HdMonoLabel`](HdMonoLabel.kt)** — auto-uppercase mono caption.
  The default label primitive everywhere — counters, breadcrumbs,
  metadata, "FILTER ↗" affordances, footer folios.
- **[`HdEntityId`](HdEntityId.kt)** — catalogue-card identifier
  greeble: `ENT-034`, `SCN-12`, `NOTE-007`. Short type prefix +
  zero-padded id, rendered as a [`HdMonoLabel`](HdMonoLabel.kt). Sits
  in folio footers and masthead stamp rows next to dates and counters
  to give every detail surface a stable "row-in-the-system" anchor.
  Caller picks the prefix (`"ENT"`, `"SCN"`, `"NOTE"`, …) — keep them
  short and uppercase.
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
- **[`HdToolButton`](HdToolButton.kt)** — 30dp square hairline
  toolbar action with a centered slot the caller fills with a glyph.
  `active = true` flips the border to `outline` and fills the
  background with `surfaceContainerHigh` so the button reads as
  "currently engaged". Use for compact dialog-toolbar toggles (log
  tail, stop) — too small for [`HdHairlineButton`](HdHairlineButton.kt)'s
  label, too chrome-light for an M3 `IconButton`. Pair with
  [`HdLogGlyph`](HdToolButton.kt) — three stacked hairlines — for
  the log/list toggle glyph.
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
- **[`HdHairlineCheckbox`](HdHairlineCheckbox.kt)** — square hairline
  checkbox. Empty when unchecked; primary fill + check glyph when
  checked. Pair with [`HdHairlineToggleRow`](HdHairlineToggleRow.kt)
  for the full row pattern.
- **[`HdHairlineToggleRow`](HdHairlineToggleRow.kt)** —
  `[ ✓ ]  Label` clickable row with optional hint line. The default
  toggle pattern; replaces M3 `Checkbox` + `Text` rows.
- **[`HdHairlineSegmentedPicker`](HdHairlineSegmentedPicker.kt)** —
  generic segmented hairline picker `[ DAY │ WEEK ]`. Cells share
  borders by overlapping 1dp; selected cell takes `onSurface` border
  and label color, the rest stay muted. Use for small enums (3–4
  values) where a dropdown would feel out of vocabulary.

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

- **[`HdNavRail`](HdNavRail.kt)** — expandable vertical rail. Takes a
  list of `HdNavRailDestination<T>` (icon + full label + short mono
  caption) plus an `expanded` flag and a toggle callback. Collapses to
  72dp (icon stacked over a 4-char `HdMonoLabel`) and expands to 208dp
  (full label beside the icon). A `secondary`-tinted hairline on the
  left edge slides between destinations when selection changes,
  replacing a pill background so the chrome stays grayscale.
- **[`HdBottomBar`](HdBottomBar.kt)** — slim 64dp icon-plus-mono-caption
  bottom bar; the phone counterpart to `HdNavRail`. Same data-driven API
  (`HdBottomBarDestination<T>` list + `selectedId` + `onSelect`). A
  `secondary` indicator slides along the **top** edge between cells when
  selection changes (the rail uses the left edge). Replaces M3
  `NavigationBar` because 80dp + gesture inset is too heavy for a
  writing app.

---

## Patterns

Recurring multi-component layouts that aren't a single `Hd*` primitive
yet, but show up on enough screens that "do it the same way" matters
more than "extract it now." When a third screen reaches for one of
these, lift it into a real `Hd*` component.

### Responsive layouts

Hammer runs from a 360dp phone to a 1440dp desktop window in the same
codebase. Screens decide their own layout — there is no
`isPhone()` short-circuit. Two tools, used for two different scopes:

**1. Page-level —
[`LocalScreenCharacteristic`](../ScreenCharacteristics.kt).** Provided
once at the application root by `SetScreenCharacteristics(...)`. Read
this when a *screen* needs to pick a layout against the **window** it
lives in (e.g. nav rail vs. bottom bar, two-pane vs. single-pane).
The characteristic exposes:

| Field                | Type                    | Use for                                                                                |
|----------------------|-------------------------|----------------------------------------------------------------------------------------|
| `isWide`             | `Boolean`               | Project-wide binary cut. Default reach for screen layout.                              |
| `windowWidthClass`   | `WindowWidthSizeClass`  | Three-bucket Material 3 size class (Compact/Medium/Expanded) when binary isn't enough. |
| `windowHeightClass`  | `WindowHeightSizeClass` | Vertical layout decisions on landscape phones / short windows.                         |
| `needsExplicitClose` | `Boolean`               | Add an explicit close affordance on platforms without a system back gesture (desktop). |

**2. Component-local — `BoxWithConstraints`.** Read this when the
decision is about the **container** the component is rendered in, not
the window — modals that cap their own width, side panels, embedded
detail surfaces. A 1080dp modal on a 1440dp window should still drop
to a single-pane layout when the user shrinks the window past the
modal's responsive threshold; reading `LocalScreenCharacteristic`
would miss that.

Rule of thumb: **screens read the window, components measure
themselves.**

#### Common transformations

When the available space drops below a threshold, lay out the same
content in a Compact-friendly form:

| Wide layout                                          | Compact equivalent                                                                         |
|------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Side rail + content (sticky chapter nav, side TOC)   | Top dropdown over the same content                                                         |
| Multi-column row of stat tiles                       | Vertical stack — use [`HdResponsiveStrip`](HdResponsiveStrip.kt)                           |
| Two-pane master/detail                               | Single pane with navigation                                                                |
| Inline search field in toolbar                       | Search glyph that swaps in via `AnimatedContent` (see browse-screen-toolbar pattern below) |
| Trailing meta on title row (`§ N  Title  ───  meta`) | Hide meta — title row only competes with affordances                                       |
| Modal with masthead padding (`Padding.XXL`)          | Full-bleed sheet, no outer padding                                                         |

The shared move: **never compress; always reflow.** If a Wide
component would clip or truncate at Compact, restructure rather than
shrink. Mono labels, hairline rules, and square corners stay the
same — the *arrangement* changes.

#### Thresholds

- **`isWide`** is the project's binary threshold (currently 720dp on
  most screens, 600dp where noted). Use this as the default cut.
- For finer control reach for the M3 `windowWidthClass`:
	- **Expanded** ≥ 840dp — desktop, large tablets in landscape.
	- **Medium** 600–839dp — tablets, large phones in landscape.
	- **Compact** < 600dp — phones in portrait.
- Component-local `BoxWithConstraints` thresholds are picked per
  component and named in code (e.g. `private val WideThreshold =
  720.dp`). Don't sprinkle magic numbers — name them.

#### Worked example — `OutlineOverviewUi`

The story-outline modal caps itself at 1080×880dp, so its layout is
driven by `BoxWithConstraints` against the modal's own width, not
the window's. Above 720dp the modal renders a 240dp left rail plus a
reading column; below 720dp the rail collapses into a top dropdown
above the same reading column. The reading content itself is
identical — only the chapter-jump affordance reflows.

### Dialog masthead

The standard masthead for any modal dialog —
[`FormDialog`](../FormDialog.kt),
[`ConfirmationDialog` / `IndexStripDialog`](../ConfirmationDialog.kt),
and [`GlobalSearchUi`](../../globalsearch/GlobalSearchUi.kt) all use this
shape, and any new dialog should too:

```
  § MARKER ──────────────── META  ×
  ════════════════════════════════
  ────────────────────────────────
  <body content>
```

- **§-marker row.** A small-caps mono caption on the left, optional
  matching mono meta caption on the right (`PROJECT`, `12 RESULTS`,
  `LAST EDIT 14:32` — must obey the [greeble rule](#greebles): real
  values, not fabricated). On desktop add a trailing `×` close glyph
  after the meta, since the dialog has no system back gesture.
- **[`HdFolioDivider`](HdFolioDivider.kt) underneath.** The 2dp +
  hairline rule stack is what gives the dialog its "title bar"
  weight without a shadow.
- **Body.** Padded `start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22–26.dp`
  (the wider gutter sets dialogs apart from scrolling screens, which
  use `Padding.XL = 16.dp`).

The marker row is **not** an `HdSectionHeader` — that primitive is
reserved for in-screen sections and renders a roman-numeral marker
(`§ I`). Dialogs use a single short keyword (`§ RENAME`, `§ DELETE`,
`§ SEARCH PROJECT`) which doubles as the dialog's accessible title.
Pass the `§` as a literal character in the marker string.

Exact mono styling, copied so far in each call site:

```kotlin
fontFamily = FontFamily.Monospace,
fontSize = 10.sp,
fontWeight = FontWeight.Medium,
letterSpacing = 1.8.sp,
color = MaterialTheme.colorScheme.onSurfaceVariant,
```

(Destructive variants — `IndexStripDialog(destructive = true)` —
swap the marker color to `error`.)

Three occurrences now share this shape, so per the
[Adding to the system](#adding-to-the-system) rules this is the next
candidate to lift into a real `Hd*` primitive (working name:
`HdDialogMasthead`). Until that lands, copy the exact styling above
rather than improvising — the cohesion comes from the consistent
metrics, not just the layout.

### Responsive browse-screen toolbar

The shared shape of every list-of-things screen — Encyclopedia
([`BrowseEntriesUi`](../../encyclopedia/BrowseEntriesUi.kt)), Notes
([`BrowseNotesUi`](../../notes/BrowseNotesUi.kt)), and any future
"browse + filter + search a collection" screen.

The header reads `§ Roman  Title  ──────  meta`, sits above an
[`HdFolioDivider`](HdFolioDivider.kt), and is followed by a single
**filter strip** that holds the search field, the type/tag filter,
and any per-screen affordances (sort menu, "+ new" button). Below
that, the actual content (grid, list) scrolls.

Two behaviors give the pattern its character:

**1. Three-state width response.** Driven off
`LocalScreenCharacteristic` — read both `isWide` (binary, project-wide
threshold) **and** `windowWidthClass` (M3 size class, three buckets):

| Width                                         | Title row                  | Filter strip                                       |
|-----------------------------------------------|----------------------------|----------------------------------------------------|
| **Expanded** (`windowWidthClass == Expanded`) | `§ N  Title  ───  meta`    | `[Search] │ [filters …] [trailing]` (one line)     |
| **Medium** (`isWide && !Expanded`)            | `§ N  Title  ───  meta`    | `[Search]` then `[filters …] [trailing]` (stacked) |
| **Compact** (`!isWide`)                       | `§ N  Title  ─────────  ⌕` | `[filters …] [trailing]`                           |

The Compact title row swaps the section header for an inline
[`HdSearchField`](HdSearchField.kt) + `×` close icon via
`AnimatedContent` when the user taps the search glyph. The header's
trailing meta is hidden in Compact since it competes with the icon
for space. Rule of thumb: anything that lives "in the toolbar" on a
desktop window must have a Compact home — either inline at the end
of the filter row (sort, "+ new") or behind the title-row icon
toggle (search).

**2. Scroll-collapsing strip.** The whole filter strip is wrapped in
a private `CollapsingStrip(scrollBehavior)` that translates the strip
by `scrollBehavior.heightOffset` and reduces its reported height to
match, so the content grid below slides up under it. Driven by
[
`TopAppBarDefaults.enterAlwaysScrollBehavior`](https://developer.android.com/reference/kotlin/androidx/compose/material3/TopAppBarDefaults)
on `rememberTopAppBarState()`; the parent `Column` plumbs
`Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`. This
gives the strip the same scroll-away semantics as an M3 TopAppBar
without inheriting its chrome. Title row + folio divider stay put as
the masthead.

The `CollapsingStrip` helper is currently duplicated — a private
copy lives in each browse screen. Lift it into `HdCollapsingStrip`
the next time a third browse screen needs it.

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
