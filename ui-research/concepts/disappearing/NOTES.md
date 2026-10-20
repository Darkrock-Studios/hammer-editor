# Disappearing UI

**The idea.** At rest the writing view is the prose and one quiet status line (scene name, `SCN-007`, word count, sync, `Ctrl K`); everything else is summoned and leaves when you are done. Only one summoned thing is on screen at a time: the command palette, the scene details sheet, or the structure overlay, and `Esc` always returns to the prose.

States: `?state=resting|palette|details|structure` on the writing view (also reachable with `Ctrl K` / `Esc`, the palette rows, and on phone the bar buttons). `?device=desktop|phone` picks the canvas; `&controls=0` hides the concept strip.

## How it groups
- By time first: things that are not needed now are not on screen, so most grouping problems never arise.
- Inside a summoned layer, by white space only. The details sheet is five blocks (outline, notes, tags, references, draft) separated by space, no rules. The palette has two labelled groups ("This scene", "Go to"). The structure overlay is three columns, one per chapter plus "Not in a chapter".
- Settings is one list with four headed groups. Each group sits on a single tinted plate (common region); rows inside are separated by spacing, not lines. One decision per row, label left, control right. Dependent rows (theme colors) are indented and dimmed while their switch is off.

## How it handles emphasis
The ladder is carried by typeface as well as weight and colour, so it reads at a distance:
1. **Serif (Newsreader), full ink: the writer's own words.** Prose, scene and chapter names, outline, notes, tags, author, chosen values. If it is serif, you wrote it.
2. **Sans (Instrument Sans), mid ink: the app talking.** Labels, commands, headings.
3. **Mono (IBM Plex Mono), quiet ink: real metadata.** `SCN-007`, `1,240 W`, sync time, shortcuts, entry types.
4. **Empty is drawn as empty.** Placeholders are faint sans inside a dashed outline with no fill, so "Add a subtitle" can never be mistaken for a value.

## What makes it distinctive
- Almost no chrome, but not blank: large confident type, a measured column with generous margins, and one signature accent (beacon vermilion, warmer and brighter in dark) used only for "where you are": the active-paragraph bar, the caret, the selected command, the current scene, switches that are on, and the `Ctrl K` key.
- The palette is the navigation. Home, Story, Notes, Encyclopedia, Timeline and settings are commands; there is no nav rail or top bar to repeat which screen you are on.
- The project title appears only where it is the subject (structure overlay, settings), so identity is stated once per surface.

## Phone (`?device=phone`, 412 x 915)
- **No hidden gestures.** The status line becomes a thumb bar with one visible target per summoned layer: a structure button on the left, the scene name in the middle (its small up-chevron opens scene details), and a search button in accent on the right that opens the command palette. Tapping an open layer's button again, the grab handle or the scrim closes it. The bar is the only chrome, and scene name, SCN-007, word count and sync still appear once.
- **Palette and details are bottom sheets.** The sheets fit their content, so the top of the prose stays visible behind a scrim. The palette opens list-first with no keyboard shortcuts shown. The soft keyboard only comes up when the search field is tapped, so commands stay in thumb reach. Details has a "Done" button in place of Esc.
- **Structure is full screen.** It shows the project title, the five destinations as a 44px tab row, and chapters stacked into one column. The structure button stays lit in the bar while it is open.
- **Settings is one scrolling column.** A back button ("Writing") and the sync time sit at the top, and the search sits under the title. Text fields and pickers stack under their labels at full width, while switches stay label left, control right. The jump list is dropped because search covers it.
- Every touch target is at least 44px: rows are 48 to 60px, and switches have an enlarged invisible hit area.

## Biggest risk
Discoverability. Summoned UI is invisible UI: new users, and anyone who does not think in shortcuts, may never find scene details or the structure without being taught, and on Android there is no `Ctrl K`. The phone bar answers this with three always-visible buttons, which is a little more chrome than the desktop status line; on desktop the single "Ctrl K Commands" hint still has to carry the whole app.
