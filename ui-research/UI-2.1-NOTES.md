# UI 2.1 notes

UI 2.1 is the scaled-back 4.0 refresh. It has two goals:

- Remove duplicate information.
- Lower density, for readers who struggle to parse the current UI.

The project library is the test case. Prototype on `4_0/ui-redesign-prototype`. Renders in `lookbook/2026-11-18-ui21-library/` (with `before-after-*.png`).

## Library: what changed

| Before | After | Why |
|---|---|---|
| Window "Hammer - Project Selection" | "Hammer" | The OS chrome names the app; the screen names itself. |
| Masthead "HAMMER · LIBRARY" (mono caps) | One page title, "Projects", in headline type | Named the app a second time; "Library" didn't match the rail's "Projects". |
| "4 ENTRIES" | Gone | Restates the list. Counted only projects, so it disagreed with the rows shown (2 groups + 3 projects). |
| Column header "№ / TITLE / LAST OPEN" + 2 rules | Gone | Rows label their own values ("Opened 11 Nov 2026"). Saves two hairlines and a row of mono caps. |
| Row numbers 01, 02, 03 | Gone | Restate list order. They aren't IDs and change with sort. |
| "Created 29 JAN '23" on every row | Gone from the row | Rarely needed; still available through sort. |
| Phone: CREATED / OPENED / WORDS label grid | One line: "35,048 words · opened 23 Oct 2026" | Six mono caps cells became one plain sentence. |
| Group "0 PROJECTS" always | Count shown only when collapsed, sentence case | An open group shows its projects. |
| Footer "LIBRARY · SYNC · ON · V3.12.1" | Gone | Library repeated the page, version repeated the rail, and sync is implied by the refresh button. |

Version now shows once (rail footer on desktop, About everywhere).

## Candidate rules

To test against more screens before trusting them.

1. **Say it once.** Each fact (app name, screen name, version, sync state) appears once per screen. The innermost, most specific place wins.
2. **One "you are here".** A screen has one title, in reading type, matching the nav label. No eyebrow labels restating it.
3. **Don't count what's visible.** Show a count only when the things counted are hidden (a collapsed group, a filtered list).
4. **Values label themselves.** Prefer "Opened 11 Nov 2026" over a column header plus a bare value. Column headers only for true tables people scan across.
5. **Greebles must carry information.** Decorative numbering that restates order goes. Stable IDs stay (the entity ID rule still holds).
6. **Mono caps are for codes and IDs, not prose.** Dates, counts and labels people read go in sentence case, in reading type.
7. **Meta reads as a sentence.** Join secondary facts on one line with " · " rather than a label/value grid.

## Design system changes (adopted on the prototype branch, 2026-11-18)

These are now in `DESIGN_README.md` on `4_0/ui-redesign-prototype`: rule 6 "Say it once", the mono vs. sans line, the greeble rewrite, the button tiers, and the masthead title rule. New components: `HdQuietButton`, `HdSplitButton`, `HdQuietSortMenu`, `HdMetaText` / `hdMetaStyle()`. Only the project library uses them so far.

### New: a button hierarchy

Today every button is framed (`HdHairlineButton`, `HdToolButton`). Three tiers instead:

| Tier | Component | Looks like | Use |
|---|---|---|---|
| Primary | `HdHairlineButton(emphasised)`, `HdSplitButton` | Framed, `primary` | The one main action on a screen. At most one per masthead. |
| Tool | `HdToolButton` | 30dp framed square, icon only | Icon actions that sit beside other framed things (a search field, a filter strip). |
| Quiet | `HdQuietButton` (new) | No frame; icon and/or label in `onSurfaceVariant` | Masthead tools: search, sort, sync. |

Rules:
- **Only the primary action is framed in a masthead.** Everything else there is quiet, so the row reads as one action plus tools.
- **A tool next to a framed element is framed too** (the sort square beside the search field). Match the neighbour.
- **Icon + label: the icon names the kind of control, the label says its current value or action** ("Recently opened", "Sync"). Never a label that repeats the icon ("Sort:" beside a sort icon).
- Labels on controls are sentence case in body type, not mono caps.

### New: split button

`HdSplitButton`: the common action, with an attached ▾ for related, rarer ones ("New project ▾" → New group). Replaces a primary button plus a separate icon button for a sibling action.

### New: state lives on its control

Show a state on the control that changes it, not in a separate caption. The Sync button's presence says sync is on, and its label shows syncing. This replaces folio captions like `SYNC · ON`.

### Changed: greebles

The README says "we want more of them, not fewer" and endorses `9 ENTRIES` and sync state as greebles. Proposed:
- Keep "a greeble has to mean something", and add **"and must not repeat something already on screen."**
- Drop `9 ENTRIES`-style counts of visible items, and folio captions that restate the page title, app name or version.
- Entity IDs, word counts and section counts still qualify.

### Changed: mono vs. sans (agreed 2026-11-18)

The README makes mono caps the label for everything ("the single most recognizable signal"). Instead:
- **Mono:** numbers, dates, codes and IDs. Short tokens people scan or compare. Equal-width digits line values up down a column.
- **Sans:** anything phrased as words to read or act on: titles, control labels, hints, descriptions.
- **Mono metadata is sentence case at regular weight with near-normal tracking** (`labelMedium`, weight 400, 0.2sp). All caps with 1.5sp tracking stays only for true greebles (entity IDs, folio stamps).
- Mono keeps the identity on secondary text; it is never spent on primary content.

### Changed: screen masthead

- The title is the screen's name in display type, matching its nav label. No mono eyebrow restating app and screen (`HAMMER · LIBRARY`).
- The trailing side is quiet tools plus at most one primary action.
- Folio footers go unless they carry something found nowhere else on the screen.

### Knock-on work if adopted

- `HdSortMenu` gains the quiet icon + value form (Story Ideas, Notes, Encyclopedia, Timeline use it).
- `HdQuietButton` and `HdSplitButton` move from the prototype (`ProjectListMastheadControls.kt`) into `designsystem/`.
- Every screen masthead and footer folio gets audited against "say it once".

## Settings: what changed (2026-11-18)

App Settings went from one 2,100dp page of eight § sections to four tabs (General, Writing, Sync, Plugins), each about one screen. Hero, breadcrumb, footer folio, roman numerals and repeated values (selected theme, "50 / 50", connection/email twice) are gone. Single-button sections became `HdSettingRow`s; desktop pickers became compact row controls; Sync lost its catalogue-card frame and got a status block (host as a mono link, email in mono); backups use a stepper; the spell check Experimental notice is gone. Rules from it are in `DESIGN_README.md` (button conventions, settings rows and groups, the "Settings screens" pattern). Adam's review notes: pickers full width when stacked on phone, a clear size step between group and row titles, one hairline under group titles.

Open: backups sit on the Sync tab even for local-only users; "Sync" may want to be "Sync & backups".

## Project settings: what changed (2026-11-18)

Same treatment as app Settings: four tabs (Project, Writing, Timelines, Plugins) replace eight § sections, breadcrumb, hero and folio. The project name shows once, as the title of the Project tab's first group (author, language, read-only sync). Theme colours appear only with the theme on, each a row with a mono hex and "Edit…"; the word goal is one row ("500 words per [Day | Week]") with "about n words a day" only for weekly goals; tag and dictionary hints are sans lines; the Timelines tab drops its "Timelines" label and its counts read "Manual · 5 events". Sync stays read-only inside a project (Adam: it simplified the implementation).

## Decisions

- 2026-11-18: Mono vs. sans rule above agreed. Mono tried at medium weight (looked dense) and regular weight; sets `lookbook/2026-11-18-ui21-sans`, `-mono-medium`, and live.
- 2026-11-18: The page title is "Projects", matching the nav label. "Library" is dropped as a name for this screen.
- 2026-11-18 review of the 4.0.0 library: footer "LIBRARY", footer version, "HAMMER" in the masthead, and the entry count are all clutter. Sync state may be worth keeping somewhere.
- Greebles stay as a concept; each one has to earn its place against the clutter it adds.

## Open questions

- Where sync state lives once the footer is gone.
- The page title "Projects" repeats the rail or bottom-bar label. Kept, because one heading per screen is orientation, not clutter.
- Rail labels are abbreviated mono caps (PROJ, IDEA, SETT, INFO). Abbreviations are a known parsing cost; consider full words.
- Sort control still reads "SORT ↓ OPENED" in mono caps.
- Strings are hard-coded English in the prototype ("Opened %s", "opened %s").
