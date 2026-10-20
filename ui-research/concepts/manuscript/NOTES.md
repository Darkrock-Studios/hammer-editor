# Manuscript

**Idea.** Hammer as a typeset book page: one calm serif column for the prose, with everything else pushed into the margins as Tufte-style sidenotes. There are no boxes, panels, cards or rules anywhere; the page grid does all the work.

**Grouping.** A fixed three-column book grid (left margin 220, column 600, right margin 244) shared by every view. Left margin is the table of contents (chapters with hanging mono numbers, scenes beneath); right margin holds scene facts, outline, notes, references and tags, each note placed level with the paragraph it sits beside and pushed down if it would collide. Settings reuses the same grid: section names hang in the left margin, label and value share the column, hints sit in the right margin. Space and alignment are the only separators.

**Emphasis.** Four steps, each a change of face as well as tone, so they do not rely on color alone:
1. User data: book serif, full ink (prose at 21px, setting values at 19px).
2. Data in the margins: same serif, smaller and slightly softer.
3. Labels and navigation: IBM Plex Sans, small, muted.
4. Empty: Plex Sans in the faintest tone with a leading `+` ("Add a tag", "Add a word").

The one accent (rubric red by day, ember at night) marks only "where you are" (current destination, current scene, the text caret) and the primary action (Save draft), plus switches that are on.

**Distinctive.** It looks like a book, not an app: Newsreader at display size for the one title per screen, indented paragraphs with no gaps, hanging chapter numbers, hanging section heads. The scene name appears once (the title); its ID (`SCN-007`) is the kicker above it. Editing tools are a quiet line at the foot under the column. Dark is "a book read at night": warm brown-black paper, parchment text instead of white, margins receding further than the column, lighter prose weight and looser leading to offset halation.

**Biggest risk.** Without any containers, discoverability depends on the writer trusting that quiet margin text is interactive; fields, switches and pickers only reveal themselves on hover, and the faint placeholder tone sits near the bottom of acceptable contrast. The grid is also rigid: narrow windows and Android need a story for where the margins go (likely collapsing sidenotes inline under their paragraph), and long scene metadata could pile up in the right margin far below its anchor.

## Phone (412 x 915, `?device=phone`)

There are no margins on a phone, so the marginalia fold away instead of being squeezed in:

- **Contents is its own page.** Tap the project name at top left ("The Lighthouse" with a chevron) and the page turns to a typeset contents page (`state=contents`): book title and author, the five destinations as one row, chapters with hanging numbers, the current scene marked in accent, then New scene, New chapter and Project settings. "Close" returns to the scene. The project name appears only once on either page.
- **Sidenotes become a hanging-label block.** The `SCN-007` kicker line is the tap target ("Details"). It unfolds the scene's words, edited time, draft, outline, notes, references and tags under the title (`state=details`), with labels hanging in an 86px column. The same label/data ladder applies, and the prose moves down with no sheet or overlay.
- **Settings** keeps the article structure. Section names sit above their rows instead of hanging, and each row puts the label on the left and the value on the right. Hints sit under their row instead of in the margin.
- **Thumb zone:** Save draft, Focus mode and Reader mode, then the formatting toolbar, sit at the bottom. Sync status moves to the top right. Every control is at least 44px.

Phone risk: the details block pushes the prose down by roughly half a screen, so on a phone the metadata and the writing compete more than they do on desktop.
