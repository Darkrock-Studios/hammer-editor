# Design system review against PRINCIPLES.md

Scope: `composeUi/.../designsystem/DESIGN_README.md`, the `Hd*` components, and the rendered screen gallery (`composeUi/build/screen-gallery/`, paths below are relative to it). Gallery screens use fake preview data; sparse lists and zero counts are a data artifact, not a finding.

Priority key: **High** = directly causes the stated parsing problems for neurodivergent readers; **Medium** = adds load or inconsistency; **Low** = polish. Principle numbers refer to PRINCIPLES.md.

---

## Part A: The maintainer's three reported problems

These are primary evidence. Each is confirmed against the gallery and the principles, with concrete changes.

### A1. Repeated information at the top and bottom of a screen (High)

**What the gallery shows**

- `tablet-light/project-settings.full.png`: top breadcrumb `← HOME / PROJECT SETTINGS / THE LIGHTHOUSE`, then `§ 0 · PROJECT` eyebrow, then the title `The Lighthouse`, then `by A. Writer`, then an `AUTHOR` field containing `A. Writer`. Footer: `PROJECT SETTINGS · THE LIGHTHOUSE · §§ 4`. The screen name appears three times and the author twice.
- `tablet-light/view-encyclopedia-entry.full.png`: breadcrumb `ENCYCLOPEDIA / TEST`, stamp `PLACE`, greeble `FOL. T · ENT-000`, title `Test`, caption `FIG. I · ENTRY · ↗` under a figure already captioned `FIG. I`, a Particulars grid with `TYPE: Place` (duplicate of the stamp), `TAGS: 2` (duplicate of the Tags section), `SCENES: 0` (duplicate of `Appears in scenes · 0`), and a footer `ENTRY · CATALOGUE CARD ... END OF ENTRY ✦`. Phone dark (`phone-dark/view-encyclopedia-entry.full.png`) shows the same plus an `EDITED` footer label with no value.
- `tablet-dark/project-list.png`: masthead `HAMMER · LIBRARY`, footer `LIBRARY`.
- `tablet-light/outline-overview.png`: masthead `MANUSCRIPT / Story Outline`, then `AN OUTLINE OF / Test` inside the body, then `CHAPTERS 2 · SCENES 3` and again `2 SCN`, `1 SCN`, `CHAPTER I · 2 SCENES` in three places.
- `tablet-light/project-stats.full.png`: `24 SCENES · 4 CHAPTERS` in the section header, then a tile `SCENES 24 / across 4 chapters` immediately under it.

**Principles violated:** 3.3 (each fact once), 1.1 (excise), 2.2 (hierarchy by strong cue, not many weak ones), T3.

**Why this hits neurodivergent readers hardest:** a monotropic or ADHD reader re-entering the screen scans for "where am I"; three candidates in three typographic styles means three things to compare and reject. COGA's own guidance for re-entry (4.2.1, 4.2.4) is *one* clear heading, not several.

**Where greebles earn their place (keep):**
- A real, changing value next to where it is used: `256 W`, `0/64` on a field, `LAST EDIT 14:32`, `AUTOSAVED`, `SYNC · ON`, the `49 W` on a note card.
- Identifiers the writer might actually search or cite: `SCN-07` next to a scene name in a stats tile, because tapping it opens that scene.
- Eyebrow above a title in a *detail* view where the breadcrumb is absent on that width.

**Where they are redundant (change):**
- Any footer that restates the masthead (`LIBRARY`, `PROJECT SETTINGS · THE LIGHTHOUSE`, `ENTRY · CATALOGUE CARD`, `END OF ENTRY ✦`). Rule: a screen footer may carry *state* (sync, version, autosave) but never *identity*.
- Section counts that duplicate a tile or list directly under them (`§§ 4` in a footer; `24 SCENES · 4 CHAPTERS` above a `SCENES 24` tile).
- Captions that repeat a caption (`FIG. I` twice).
- Particulars grids whose cells are counts of the sections below. Replace with facts that appear nowhere else (created, edited, word count of description) or delete the grid.
- Catalogue-flavour labels with no source of truth or no reader value (`FOL. T`, `CATALOGUE CARD`). The README's own greeble rule ("has to mean something") already forbids the first; extend it with "and must not be stated elsewhere on the screen."

**Proposed rule for DESIGN_README (replace the Greebles section's last paragraph):**
> A greeble must (1) encode a real value, (2) be the only place on the screen that value appears, and (3) sit next to the thing it describes. Identity (screen name, item type, item name) is stated once, in the masthead or title, never in a footer. Footers carry live state or nothing.

### A2. Everything at the same emphasis level: labels, hints, and user data (High)

**What the gallery shows**

- `tablet-light/create-encyclopedia-entry.png`: `NAME` (mono caps, muted) over `e.g. Cheshire Cat` (body, muted). `TAGS ↵ TO ADD` over `ritual, magical, recurring...` (body, muted). `Description` is a different face and case from `NAME` and `COVER ART`. There is no user data on screen yet, and a stranger cannot tell which rows are empty.
- `tablet-light/project-settings.full.png`: `AUTHOR` over `A. Writer` (real data, `onSurface`) and `WORDS` over `500` (disabled, `onSurfaceVariant`) look alike at a glance; the only difference is a grey step.
- `tablet-light/scene-editor.png` metadata panel: `OUTLINE` then `A short description of what happens in the scene.` and `NOTES` then `Notes to yourself.`: placeholders set in the same body style as real content would be.
- `phone-light/create-note.png`: `TAGS ↵ TO ADD` and `idea, todo, follow-up...` again.

**Component cause:** `HdHairlineField` renders label (`labelSmall` mono, `onSurfaceVariant`), hint (`labelSmall` mono, `onSurfaceVariant`), placeholder (`bodyLarge`, `onSurfaceVariant`) and value (`bodyLarge`, `onSurface`). Label and hint are identical; placeholder and value differ by color only. `HdHairlineTagField` inherits this. Disabled values also drop to `onSurfaceVariant`, colliding with placeholders.

**Principles violated:** 2.3 (one job per emphasis level, more than one cue), 4.2 (dyslexia: bold, not case or color, for emphasis), 4.7 (plain labels), `wcag-22/` 1.4.1 (color must not be the only cue), 6.4 (state visible in place).

**Proposed changes**

1. **Three fixed roles with two cues each.** In `HdHairlineField`, `HdHairlineTagField`, `HdHairlineWordListField`, `HdMetadataItem`, `HdInlineStat`:
   - *Label*: mono caps, `labelSmall`, `onSurfaceVariant` (unchanged). This is the only thing allowed in mono caps inside a field.
   - *Placeholder*: body size, `onSurfaceVariant` **and** a visible empty marker. Options: prefix `e.g.` always and set at `bodyMedium` (one size step down), or render as a short hint line *under* the rule rather than inside the field. The field should read as empty, not as filled with grey text.
   - *Value*: body size, `onSurface`, **medium weight**. User data is the darkest and heaviest thing in the form. Bold is the dyslexia-safe emphasis.
   - *Hint* (`↵ TO ADD`, `MARKDOWN`): never inline with the label; move to the trailing counter slot or under the rule, and drop it entirely where the affordance is obvious (tags already show chips).
2. **Disabled is not grey text.** A disabled field keeps value weight and uses a dotted rule plus reduced alpha on the whole block, so disabled values are not confused with placeholders.
3. **One label face per form.** `Description` in create-entry uses a different style from `NAME`; make the markdown editor header an `HdMonoLabel` like every other label.
4. **Empty state is a sentence, not a fake value.** The scene metadata panel should show `OUTLINE` then a muted italic-free one-liner such as `No outline yet` plus an `Add` affordance, not a placeholder sentence that reads like content.
5. **Add a `HdFieldRoles` preview** to the component reference showing label / placeholder / value / disabled / error side by side so the four states are checked together on every change.

### A3. Grouping by hairlines: on a large screen it is "a ton of lines and text" (High)

**What the gallery shows**

- `tablet-light/project-settings.full.png`: four left-edge marginalia rules, each section also containing field underlines, a bordered dropdown, bordered chips, a bordered segmented picker, and a bordered notice strip. Roughly 20 rules of three weights in one viewport.
- `tablet-light/project-stats.full.png`: three left rules, one masthead rule, vertical cell dividers in `HdHairlineGrid`, horizontal rules between tiles, bar chart edges. The left rules are so long and faint that they do not read as brackets; they read as stray borders.
- `tablet-light/timeline.png`: vertical timeline rail with square nodes, plus every event is a bordered card with a shaded header, plus bordered chips inside. Three nested frames per item.
- `tablet-light/scene-editor.png`: the metadata panel has a rule under every field; at 0 words this is six rules and six captions for no content.

**Principles violated:** 2.4 (group by region, not separator), 2.5 (white space first), T1, T4, `coga-content-usable/` 4.3.2 ("hierarchy solely dependent on small unique design elements creates confusion"), `ui-density/` (count design decisions; each line is one).

**Why the marginalia rule fails as a grouping device:** it is one device pixel, `outlineVariant`, and runs the full section height. Gestalt common-region needs an enclosed or shaded area; a single edge does not enclose. Proximity would do the job if the gutters between sections were larger than the gaps within them, but `HdHairlineSection` uses 16dp both for `contentSpacing` and section-to-section spacing on most screens, so the eye cannot tell inside from between.

**Proposed changes**

1. **Make white space the primary grouping cue.** Set section-to-section spacing to at least 2.5x the intra-section spacing (e.g. 40dp between sections, 16dp inside). This alone removes the need for most rules. Add `Ui.Spacing.Section` and use it in every long-scrolling screen.
2. **Promote one grouping cue per section, demote the rest.** Give `HdHairlineSection` a `boundary` parameter with three values: `Gutter` (white space only; default), `Region` (`surfaceContainerLow` fill with no rule), `Rule` (today's marginalia rule, for screens that genuinely need a bracket, e.g. a nested subsection). A section uses exactly one.
3. **Field underlines are not section rules.** Inside a `Region` section, `HdHairlineField` draws its rule at 50% alpha (the `ruleSoft` already used in `HdEntryCard`) so field rules read as a second tier under the section boundary.
4. **Ban nested frames.** A bordered component inside a bordered component inside a shaded region is three boundaries for one thing. Rule: at most two boundary levels visible at once. Concretely: timeline events lose either the card border or the rail; tag chips inside a bordered card lose their own border and keep only the swatch.
5. **Collapse empty metadata.** `HdHairlineField` with an empty value in a *view* (not edit) context renders as a single compact `LABEL · Add` row with no rule. The scene metadata panel at zero content becomes four short lines instead of a full-height form.
6. **Merge the `HdHairlineGrid` dividers into gutters** on wide screens: stats tiles separated by 32dp of space, not vertical rules; keep the rules only on Compact where there is no room.
7. **Audit target:** on any screen at 1440dp wide, count visible rules; the budget is one masthead rule stack, plus one boundary per section, plus field rules in the active form only. Make this a line in the checklist.

---

## Part B: Keep (already aligned)

| Rule or component | Why it aligns |
|---|---|
| **No elevation, grayscale chrome, one accent `primary`** (README rules 1 and 3) | Low-salience chrome keeps the prose the high-load target (1.3); color only where it means something satisfies `wcag-22/` 1.4.1 and the autism poster's "simple colours". |
| **Square corners, one shape family** | Consistency (6.1) and fewer design decisions to parse (`ui-density/`). |
| **Stable category colors per entry type, same in light and dark; `EntryType.glyph()` paired with a name** | Color plus glyph plus text is the low-vision poster's "combination of colour, shapes and text"; stable across runs supports recognition over recall (6.4). |
| **`HdMonoLabel` as the single caption primitive** | One shape, one meaning (6.1). Keep it; constrain where it may appear (see A1, A2). |
| **Greeble rule: must encode a real value** | Right instinct; needs the "stated once" clause (A1). |
| **Collapse chrome while typing; snaps, never animates** | Direct implementation of 1.1, 1.3, 7.3, 8.2. The README's reasoning about size animation reflowing the editor is exactly `coga-content-usable/` 4.5.1. |
| **Never compress, always reflow** (responsive pattern) | Survives 200% text and user spacing (4.3, `wcag-22/` 1.4.4, 1.4.12). |
| **`HdWarningNotice` non-blocking by design** | Interruptions that do not interrupt (1.4, 5.2). |
| **Screen masthead rule: 56dp, same padding everywhere** | Same thing in the same place (6.3) and a re-entry heading on every screen (5.4). |
| **`HdHairlineToggleRow` with hint line; `HdHairlineCheckbox` filled when checked** | State visible in place with two cues (fill plus glyph) (6.4, 5.3). |
| **`HdStatusGlyph` uses shape *and* color per status** | `wcag-22/` 1.4.1; autism poster. |
| **`HdCollapseGlyph` vs `HdClearGlyph` distinction** | Two actions, two shapes (6.1). |
| **Editor width cap and `HdResizeHandle`** | 45-90 character lines (4.1), user-controlled (8.1). |
| **`editorFontSize`, `readerFontSize`, `editorMaxWidth` settings** | The seed of the presentation settings surface (8.1). |
| **Scene editor on phone: title, toolbar, prose, nothing else** (`phone-light/scene-editor.png`) | The target posture for the whole app (1.1, 2.1). |
| **Sync conflict screen** (`tablet-dark/sync-conflict.full.png`): two clearly labeled columns, one filled action each, diff highlighted | A model of 2.1, 2.6 and 5.3. The step indicator `CONTENT / METADATA / REFERENCES` is COGA 4.2.4. |

---

## Part C: Change

Each item: component or rule, problem, principle, proposed change, priority. Items A1-A3 above are the top three and are not repeated here.

### C1. `§` markers and Roman numerals as section identity (High)
*Problem:* `§ I Structure`, `§ IV Timeline`, `§ III · NEW`, `§ 0 · PROJECT`, `§ - Particulars`, `§ I.1 The Harbour`. The numeral is sometimes an ordinal, sometimes a nav index, sometimes a dash. The reader must decode a symbol and a numeral to get no information the title does not already give. Inconsistent (`§ -` vs `§ I`) which COGA 4.2.3 names as a failure.
*Principles:* 4.7, T3, `coga-content-usable/` 4.4.6 (avoid Roman numerals), 6.1.
*Change:* Drop the marker from screen mastheads and dialog mastheads (`§ NEW Create entry` becomes `Create entry`; `§ RENAME` becomes `Rename`). In long-scrolling screens, keep a section *number* only when the sections are a sequence the writer navigates (outline chapters), and render it as an Arabic numeral with a plain word: `Chapter 2`, not `§ II`. Keep `§` purely as a scene-break ornament in reader mode where it has typographic meaning and no informational job.

### C2. Mono-caps abbreviations in headers (High)
*Problem:* `1 · 0 GR` (`tablet-light/scene-list.png`), `2 SCN`, `14 W · 74 CH`, `§§ 4`, `FOL. T`, `EN-US ▾`, `OFF · SYSTEM`. These require decoding and several have no plain twin.
*Principles:* 4.7, `bda-dyslexia-style-guide/` Pagination ("page x" not "pX"), `coga-content-usable/` 4.4.1.
*Change:* `HdMonoLabel` gets a lint-style rule in the README: a greeble may abbreviate only units that are universally read (`W` for words is borderline; `WORDS` is safer), never nouns. Replace `GR` with `groups`, `SCN` with `scenes`, `CH` with `chars` or drop it. Where space is tight on Compact, hide the meta (the responsive pattern already permits this) rather than abbreviate it.

### C3. `HdMonoLabel` is the default for too many jobs (High)
*Problem:* The README says mono caps is "the single most recognizable signal of the design" and the system reaches for it for labels, breadcrumbs, counters, nav captions, button text (`BROWSE FILES`, `USE Remote`), filter cells, chip text, table headers, empty-state captions, and footers. Caps plus wide tracking plus 10-11sp is the slowest-reading combination for dyslexic readers (4.2) and when everything is a mono caption nothing is a label (2.3).
*Principles:* 2.3, 4.2, 4.4.
*Change:* Restrict mono caps to three jobs: field labels, numeric greebles, and section eyebrows. Buttons, nav labels, chips, filter cells, breadcrumbs and empty states use `labelLarge`/`bodyMedium` in sentence case. Raise `labelSmall` from Material's 11sp to 12sp and reduce tracking from 1.5sp to about 1.0sp; both are cheap wins for legibility. Keep the mono *face*; the problem is caps and size, not the face (`good-fonts-dyslexia/` found monospaced faces reduced fixation duration).

### C4. Display weight `Light` for titles and big numerals (Medium)
*Problem:* `displayLarge`/`displayMedium` are `FontWeight.Light`; `HdChapterHeader` and `HdChapterRailItem` use Light. Thin strokes at large sizes lower effective contrast on dark theme and for low vision.
*Principles:* 4.4, `wcag-22/` 1.4.3, 1.4.11.
*Change:* Default titles to `Normal`; reserve `Light` for the single hero numeral on the stats dashboard where size compensates. Verify contrast in dark theme.

### C5. Placeholder and empty-state copy that reads as content (High)
*Problem:* See A2. Also `<no outline>` in italics in `tablet-light/outline-overview.png`; italics are the one thing the dyslexia evidence says to avoid.
*Principles:* 4.2, 2.3.
*Change:* Ban italics in the system (README rule). Empty states use a muted, roman, sentence-case line plus an action (`No outline yet · Add`).

### C6. Controls that do not look like controls (High)
*Problem:* `FILTER ↗` on `HdTypeStamp`, `↗ OPEN` on `HdEntryCard`, `+ tag`, `+ alias`, `EDIT` in project settings, `+ ADD` under the figure, the `⤒` collapse glyph. These are mono captions with no border or verb a stranger would recognize.
*Principles:* 6.2, `coga-content-usable/` 4.2.5 (make borders of controls clear; non-clickable things must not look like controls), T6.
*Change:* Every tappable text is either an `HdHairlineButton` (bordered, sentence case, verb) or an icon button with a label. `HdTypeStamp` keeps its stamp look but the action half reads `Filter by Place`. The entry card's whole surface is the control; drop `↗ OPEN` (the card already opens).

### C7. `HdSectionHeader` trailing meta competes with the title (Medium)
*Problem:* `§ I Structure ─────── 24 SCENES · 4 CHAPTERS`: the title, a long rule, and a mono summary on one line; the rule draws the eye to the meta, which is often redundant (A1).
*Principles:* 2.1, 3.3.
*Change:* Remove the inline rule from the header (the section boundary comes from A3's `boundary`). Trailing meta allowed only when it is not restated in the section body.

### C8. `HdNavRail` collapsed labels (`HOME`, `STRY`, `NOTE`, `ENCY`, `TIME`) (Medium)
*Problem:* Four-character mono abbreviations for primary navigation.
*Principles:* 4.7, 6.2.
*Change:* Collapsed rail shows icon plus a full short word (`Home`, `Story`, `Notes`, `Wiki`, `Time`) in sentence case at 11-12sp; widen to 80dp if needed. Or default the rail to expanded on Expanded width and persist the user's choice (it already does).

### C9. Nested scroll and panel stacking (Medium)
*Problem:* Scene editor on wide layouts: a scrolling prose column beside a scrolling metadata panel; browse screens with a scroll-collapsing filter strip over a scrolling grid.
*Principles:* 2.7, `coga-content-usable/` 4.2.6.
*Change:* Metadata panel on desktop becomes a non-scrolling sheet that collapses empty sections (A3.5), or a modal like Compact. Keep the collapsing strip but make it a user setting (`Keep filters visible`) rather than scroll-driven motion (5.1).

### C10. Animations without a reduce-motion path (Medium)
*Problem:* Seven `Hd*` files animate (rail width and indicator slide, dropdown rotation, `AnimatedVisibility` expand, `AnimatedContent` cross-fades, scroll-away footer, collapsing strip). No `reduceMotion` setting exists in `GlobalSettings`, and the OS setting is not read.
*Principles:* 7.1, 7.2, `wcag-22/` 2.3.3.
*Change:* Add `LocalReduceMotion` provided from a global setting (default: follow OS). Every `animate*AsState` and `AnimatedVisibility` in `designsystem/` takes its spec from `HdMotion.spec()` which returns `snap()` when reduced. Size animations (`expandVertically`) become cross-fades even when motion is on (7.3).

### C11. README goal "Pleasing and Enticing: Animation is attractive" (Medium)
*Problem:* Contradicts "Editorial calm" and 7.1 for a writing tool.
*Change:* Reword: "Motion only to explain a state change the writer caused; under 200ms; never on the writing surface; always reducible."

### C12. `HdEntryCard` and note cards: borders, header shade, chips, footer, all at once (Medium)
*Problem:* `phone-light/browse-notes.png`: each card has a border, a shaded header row with two greebles, body, bordered chips. With real data (dozens of notes) this is dense chrome around short prose.
*Principles:* 2.4, 3.1, T1.
*Change:* One boundary per card (shade or border, not both plus a header band). Date and word count move into one quiet footer line; chips lose borders inside cards (swatch plus text).

### C13. Dashboard tiles with mixed emphasis (`tablet-light/project-stats.full.png`) (Medium)
*Problem:* `+0` in `primary` blue, `9` timeline events in blue, everything else black; `NO DATA LAST WEEK` as mono caps under a hero number; `0 days` streak as a hero. Color is used as decoration for some numbers, and zero-state goals are shown with the same weight as real data.
*Principles:* README rule 3 (color is semantic), 8.4 (streaks and goals quiet and opt-in), 2.1.
*Change:* `primary` on a number only when it is an actionable link. Streak and goal tiles hidden until the goal is enabled in project settings. Zero or missing data renders as a small muted line, not a hero numeral.

### C14. Breadcrumbs as the only orientation on detail screens (Low)
*Problem:* `← ENCYCLOPEDIA / TEST` in mono caps is both the back control and the location. Caps breadcrumb plus the title below is a duplicate (A1).
*Change:* Back control is an icon button with `Encyclopedia` as its label; the title is the heading. Drop the caps path.

### C15. `HdHairlineSegmentedPicker` and `HdEntryFilterBar` selected state (Low)
*Problem:* Selected cell differs by border color and a 2dp stripe; on dark theme the difference is faint (`tablet-light/create-encyclopedia-entry.png` shows it works in light).
*Principles:* 6.4, `wcag-22/` 1.4.11.
*Change:* Selected cell gets fill (`surfaceContainerHigh`) plus the stripe plus medium weight label: three cues.

### C16. Dialog masthead styling copied by hand (Low)
*Problem:* The README admits the dialog marker row is copied in three places. Drift is inevitable.
*Principles:* 6.1.
*Change:* Lift `HdDialogMasthead` now, with the marker removed per C1.

---

## Part D: Add

### D1. Presentation settings (High)
A single settings page, reachable from every screen's overflow, with: UI font size scale (separate from editor and reader sizes, which exist), line height (1.3 / 1.5 / 1.7), letter spacing (normal / wide), editor and reader typeface (system sans, the bundled mono, and a dyslexia-friendly sans such as Verdana or Atkinson Hyperlegible), contrast (default / high / soft-black), reduce motion (follow OS / on / off), density (comfortable / compact), and notifications (which banners may appear and when). Sources: 4.3, 8.1, 8.5, `bda-dyslexia-style-guide/` personalisation list, `coga-content-usable/` 4.9.4. Everything in `designsystem/` must survive all combinations (`wcag-22/` 1.4.12).

### D2. Grouping rules in the README (High)
Add a "Grouping" section: white space first; one boundary cue per group; max two boundary levels visible; section gap at least 2.5x inner gap; controls inside the region they affect, labeled to match the heading; one scroll region per screen. (A3, 2.4-2.7.)

### D3. Emphasis ladder (High)
Document the four levels and their cues: *Title* (one per screen, `headlineSmall`+, `onSurface`); *Label* (mono caps `labelSmall`, `onSurfaceVariant`); *Value* (body, `onSurface`, medium weight); *Hint/placeholder* (body, `onSurfaceVariant`, with an explicit empty marker, never italic). Any new component maps each piece of text to one of these. (A2, 2.3.)

### D4. Label and copy rules (High)
Sentence case for buttons and navigation; verbs on buttons (`Add tag`, not `+ tag`); no abbreviations of nouns; no Roman numerals outside reader-mode ornament; no italics; no idioms. Add a glossary of the words the app uses for its own nouns (Scene, Chapter, Group, Entry, Note, Event) so screens agree. (4.7, C1, C2, C6.)

### D5. Identity-once rule (High)
Screen name, item name, and item type are each rendered once per screen, in the masthead or title. Footers carry live state only. (A1.)

### D6. Motion rules and `HdMotion` (Medium)
As in C10: a single place for durations and specs, `snap()` under reduce-motion, no size animation, nothing animates on the prose surface. Document allowed motion: selection indicator slide (optional), cross-fades, and nothing else.

### D7. Interruption policy and `HdNoticeStrip` scheduling (Medium)
Notices are queued and shown only at natural pauses (screen change, editor blur) unless the user opted into immediate display; a setting lists notice types with `Show now / Show later / Never`. Sync progress is a small static state glyph in the masthead, not a strip, while the user is typing. (1.4, 5.2.)

### D8. Simplified mode (Medium)
A single toggle ("Just write") that hides nav rail, toolbars, metadata, stats, and greebles across the app, not only in the editor. Reversible in one tap from the same place. Focus mode is the seed; this generalizes it. (8.2, `coga-content-usable/` 4.9.3.)

### D9. `HdEmptyState` component (Medium)
Muted sentence plus one action, used by every list and field in view mode, replacing placeholder sentences and `No X yet` ad hoc text. (C5, A2.4.)

### D10. Re-entry heading rule (Low)
Every screen's masthead names the screen *and* the current item in plain words, and edit screens show `Editing` state visibly. Already mostly true; write it down. (5.4.)

### D11. Review checklist in the README (Low)
Copy the 16-point checklist from PRINCIPLES.md into `DESIGN_README.md` and require it in PR descriptions for screen changes, with a line-count budget per A3.7.

---

## Part E: Priority summary

| Rank | Item | Impact on stated problems |
|---|---|---|
| 1 | A3 + D2: grouping by region and white space, one boundary per group | High: directly fixes "ton of lines", unclear grouping |
| 2 | A2 + D3: emphasis ladder, bold user data, explicit empty states | High: label/hint/data confusion |
| 3 | A1 + D5: identity once, footers carry state only | High: redundant information |
| 4 | C1 + C2 + C3 + D4: plain words, fewer mono caps, no abbreviations, no Roman numerals | High: dyslexia/autism parsing; density |
| 5 | D1: presentation settings (type, spacing, contrast, motion, density) | High: no single default serves this audience |
| 6 | C6: controls look like controls | High: autism/ADHD discoverability |
| 7 | C10 + D6 + D7: motion and interruption control | Medium: focus protection |
| 8 | C12, C13, C9 | Medium: density on real data |
| 9 | C4, C5, C15 | Medium: legibility |
| 10 | D8, D9, C8, C14, C16, D10, D11 | Low to medium |

---

## Part F: Open questions

**Decided (maintainer):** every view-entity screen shows its entity ID greeble; IDs may be promoted to a more prominent role later. The story idea view currently has none.

1. **Greeble budget.** The maintainer likes greebles. How many per screen is the ceiling, and which jobs (counters, ids, state) keep them? Suggest: test two variants of the entry detail screen (current vs. identity-once) with three to five neurodivergent writers and measure re-entry time and preference. (9.1, 9.3)
2. **Mono caps as label face.** Is the mono *caps* label negotiable, or only its frequency? Sentence-case mono (`Name`, `Outline`) keeps the face and halves the decoding cost; it changes the look noticeably. Needs a decision. **Decided:** sentence-case mono.
3. **Roman numerals in the outline.** Chapter numerals are part of the manuscript feel. Keep `I, II` in the outline and reader only, with Arabic everywhere else? Or offer a setting? **Decided:** prototype without them; users report they don't match their actual chapter numbers.
4. **Marginalia rule.** Retire it as the default grouping device, or keep it as a tertiary accent inside `Region` sections? Prototype both on project settings at 1440dp and compare rule counts.
5. **Default density.** Comfortable (more white space) as the default and compact as the opt-in, or the reverse? `ui-density/` argues experts want compact; COGA argues the opposite. Needs user data on how many Hammer writers use desktop at full width.
6. **Dyslexia-friendly typeface.** Bundle one (licensing, size) or rely on system fonts plus a picker? Evidence favors common sans faces over purpose-made dyslexia fonts (`good-fonts-dyslexia/` §6), so a picker over system fonts may be enough.
7. **Notification policy defaults.** Should sync and update notices default to `Show later` for everyone, or only when the simplified mode is on?
8. **Streaks and goals.** Keep them at all? `adhd-tech-neurodivergent-readers/` is skeptical of externally imposed regimes; some writers like them. Proposal: off by default, never shown as a hero when zero. **Decided:** off by default.
9. **Desktop metadata panel.** Sheet, modal, or collapsible sidebar? Depends on how often writers edit metadata while drafting; needs observation.
10. **Testing pool.** Who are the neurodivergent testers, and can we run a short remote session per milestone? Method per `coga-content-usable/` §5.4.1 (short tasks, mood scale, no blame, stop on distress).
