# Concept brief: radical redesign directions (shallow)

Hammer is an offline-first story editor (desktop + Android) that writers live in for hours. We are exploring radically different design languages for the 4.0 client before committing to one. These concepts are **rough and shallow**: two views, desktop size, enough to judge the direction. Not production code.

## Why we are doing this
- Neurodivergent users (ADHD, autism, dyslexia) report trouble parsing the current UI.
- Maintainer's own problems with the current design: the same "which screen am I on" information repeated at top and bottom; labels, hints and user data all at one emphasis level; grouping drawn with many hairlines, so large screens look like "a ton of lines and text".
- What the maintainer likes about the current design: it is **striking** and looks different from generic Material apps. Incremental fixes (tinted regions, spines, notches, textures) read better but looked generic or heavy. We need something that is both clear and distinctive.

## Non-negotiables (from our research, apply in every direction)
- Clear grouping: a person should see what belongs together at a glance.
- An emphasis ladder of at most 3 to 4 levels: user data is the strongest; labels are quieter and visibly different from data; hints/placeholders are visibly "empty".
- State identity once: the screen/item name appears once, not in a breadcrumb, a title and a footer.
- No Roman numerals, no `§` section numbering, no fabricated decorative labels. Small "greebles" (mono metadata like `SCN-007`, `1,240 W`) are welcome when they show a real value, a few per screen.
- Every entity view shows its entity ID (the scene's `SCN-007`).
- Sentence case for buttons, navigation and labels (mono is fine, all-caps is not required).
- No italics for UI text. Plain backgrounds behind text (no patterns).
- Writing goals and streaks are off by default; do not show them.
- Respect `prefers-reduced-motion`; keep motion minimal anyway.
- Never use em dashes in UI copy.

## Content (use exactly this, so the four concepts compare fairly)
Project: **The Lighthouse**, by A. Writer. Navigation destinations: Home, Story, Notes, Encyclopedia, Timeline. (Story ideas lives outside a project; ignore it.) Sync status: synced 2 min ago.

### View 1: Writing (the main editor)
- Story structure: Chapter 1 "Arrival" with scenes "The Harbour" (SCN-001), "The Lamp Room" (SCN-007, **currently open**); Chapter 2 "The Dark Night" with scenes "Midsummer" (SCN-012), "The Wreck" (SCN-004); an unfiled scene "Rumours" (SCN-015).
- Open scene: **The Lamp Room**, SCN-007, 1,240 words, edited 12 min ago, draft "Second pass".
- Prose (show 4 to 6 paragraphs; this is the most important thing on screen):
  > One hundred and nine steps. He counted them the first night and never again, because the number did not change and the counting made the stair feel longer.
  >
  > The lamp room smelled of oil and old brass. Somebody had left a chair facing the sea, and a cup beside it, and in the cup a dry brown ring where the tea had been.
  >
  > He sat. The chair took his weight the way a boat takes a wave, grudging and then all at once.
  >
  > Below, the town had already started to light its windows, one by one, as if it were answering him.
- Scene metadata: Outline "Elias climbs the tower for the first time and finds signs someone left in a hurry." Notes "Plant the cup here; pay it off in chapter 3." Tags: `tower`, `foreshadowing`. References (encyclopedia entries): Elias (person), The Lighthouse (place), The Keeper's Cup (thing).
- Editor tools exist (bold, italic, headings, lists, undo/redo), plus save draft, focus mode and reader mode actions. Decide how present they are.

### View 2: Settings (a slice of Project settings)
- Project info: Author "A. Writer" (text field), Language "English (United States)" (picker).
- Custom theme: toggle "Use a custom theme for this project" (off), Primary #455A64, Secondary #FFB300.
- Tags: `fantasy`, `draft`, with a way to add.
- Spell checking: "Enable spell checking" (on), "Enable in focus mode" (off), Dictionary "English (United States)", "Learn encyclopedia names in this project" (on, with hint "Entry names won't be marked as misspelled"), Project dictionary words: "Elias", "Keeper's".
- Show at least one empty field with a placeholder so the label / value / placeholder distinction is visible.

## Deliverable
A single self-contained `index.html` in your concept folder:
- Fixed 1280×800 desktop canvas per view (the page can be larger, but each view is designed for 1280×800).
- Two views switchable with a small control **outside** the design (e.g. a fixed tab strip in the top-right corner of the browser page, clearly not part of the app), plus a light/dark toggle there. Also support URL parameters `?view=writing|settings&theme=light|dark` so screenshots can be scripted.
- Both light and dark themes must be fully designed, not an inverted afterthought.
- Fonts: Google Fonts only (IBM Plex Mono is the app's current mono; choose other faces to suit the direction). No other external resources; inline CSS/JS, inline SVG icons.
- Make it look intentional and striking. Push the direction hard; this is a brainstorm, and timid versions are useless for choosing.
- Also write a short `NOTES.md` beside it: the idea in two sentences, how it groups, how it handles emphasis, what makes it distinctive, and the biggest risk.
