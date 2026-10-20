# Swiss poster

**Idea.** The screen is a poster set on a strict four-module grid (304 / 312 / 312 / 312 px with 8 px gutters), where every group is a solid rectangle and the gutters are the only "lines". Chrome is loud (60 to 100 px grotesk, one hot red field per screen), and the prose sits in a calm 560 px column of Inter on plain paper.

**Grouping.** Columns and solid fields only, no hairlines anywhere. In Writing: story structure is one grey field (module 1), the editor spans modules 2 and 3 on bare paper, scene details are a stack of fields in module 4. In Settings each section is one field on the same grid; Spell checking spans three modules and splits internally on the same column lines. Row 1 headings reserve two lines, so all three sections' controls hang from one shared horizontal line.

**Emphasis.** Three levels plus greebles. Data: ink, heavy grotesk (titles, values, scene names), with user-written text (prose, outline, notes) in Inter so it reads as "yours". Labels: 12 px semibold grey, sentence case. Hints and placeholders: lighter grey, regular weight, on an empty inset block. Mono (IBM Plex Mono) only for real values: `SCN-007`, entity types, tag names, hex colors, sync time.

**Distinctive.** Scale contrast and color fields. The word count is a 100 px number on a red block; chapter numbers are 60 px; section headings are 44 to 68 px. Red marks the one thing that matters on each screen (open scene plus its count; the settings title). Navigation state is a solid ink block. Dark theme is not an inversion: the red turns hotter (#FF4B26) and type on it flips to black, and the active-nav block becomes bone white.

**Biggest risk.** Volume. The giant headings and red fields could feel shouty over a multi-hour session, and they compete with the prose for first glance; a real build would need a focus mode that drops the chrome entirely. The rigid grid also leaves awkward empty space (bottom of the story column, Spell checking) and will be hard to keep honest on small Android screens, where four modules collapse to one.

## Phone (412 x 915, `?device=phone`)

The four desktop modules collapse to one column of full-width fields with the same 8 px gutters. Writing keeps prose on screen and pushes everything else into two solid bars you tap. At the top, the chapter bar ("1 Arrival", Scenes) opens the story structure. At the bottom, a red word count bar ("1,240 Words", Details) opens the details. Both open as full-height layers between the app bar and the bottom nav (`state=structure`, `state=details`). In the details layer the count grows back to 100 px poster size. Formatting tools are a quiet strip above the count bar. Focus, Reader and Save draft sit on the scene ID row. Navigation moves to a bottom bar of text cells with an ink block for the current place. Settings is one long scroll: red title block, then one field per section with 38 px headings (`state=spell` scrolls to Spell checking). Every target is at least 44 px: rows, chips with their remove buttons, switches, icon buttons.

Phone risk: the red count bar is always visible, and on a small screen it is the loudest thing next to the prose.
