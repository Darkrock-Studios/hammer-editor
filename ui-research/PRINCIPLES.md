# UI principles for the 4.0 client redesign

Working document for planning discussions. Scope: desktop and Android Compose clients. Each principle is an imperative, a reason, and a citation into `ui-research/` (folder name, then section or page). Items marked **(synthesis)** are our own inference from the sources, not something a source states.

The three problems this must solve: information density, unclear visual grouping, redundant information; weighted toward users with ADHD, autism, and dyslexia who report trouble parsing the UI.

---

## 1. Purpose and posture

**1.1 Treat the prose as the task and everything else as excise.**
Navigation, chrome, and metadata are a cognitive or physical penalty paid to reach the goal, not the goal. Every non-prose element must justify the attention it takes.
`magic-ink/` "Interactivity considered harmful" (Cooper's excise, "navigation is nothing but excise"); `nielsen-10-heuristics/` #8 ("every extra unit of information competes with the relevant units").

**1.2 Design for a monotropic reader: one attention tunnel at a time.**
Autistic attention tends to be deep and narrow; anything outside the current interest is hypo-attended, and an unanticipated change inside the tunnel is experienced as abrupt and distressing. ADHD readers describe hyperfocus the same way. Build screens that assume the writer is in one task and will not notice, and does not want, anything peripheral.
`monotropism-attention/` pp. 142-143 (hyper-awareness inside the tunnel, hypo-awareness outside), p. 147 ("every unanticipated change is abrupt and ... catastrophic"), p. 153 heuristics ("reduce task demands in complexity, time pressure and irrelevant stimuli"); `adhd-tech-neurodivergent-readers/` §6.1 (hyperfocus; attention is contextual).

**1.3 Make the writing surface itself the high-load task, and keep the rest low-contrast.**
Load theory: when the attended task is perceptually demanding, distractors are not perceived; when it is light, spare capacity spills over to whatever else is on screen. Prose is the demanding task. Anything else visible should be low enough in contrast and salience that it does not win the spillover.
`perceptual-load-theory/` §1 (capacity "spills over" under low load), §5 (high load acts like turning down the contrast of unattended stimuli).

**1.4 Never interrupt the writer.**
Interruptions, even same-topic ones, raise stress, frustration, time pressure and effort measurably within 20 minutes; people compensate by working faster and writing less. Sync banners, update prompts, toasts and reminders are interruptions.
`interrupted-work-cost/` Results and Discussion ("after only 20 minutes ... significantly higher stress, frustration, workload"); `coga-content-usable/` 4.6.1 Limit Interruptions; `wcag-22/` 2.2.4 Interruptions (postponable or suppressible).

**1.5 Solve for one, extend to many.**
Designing for a permanent cognitive mismatch (dyslexia, ADHD) also serves situational ones (tired, late-night, on a phone). This is why neurodivergent needs get heavy weight: they are the strictest constraint and the result is better for everyone.
`microsoft-inclusive-design-toolkit/` "Solve for one, extend to many" (pp. 34-42, Persona Spectrum).

---

## 2. Hierarchy and grouping

**2.1 Give every screen one obvious primary thing, visibly bigger than everything else.**
"The main feature is much bigger than anything else"; five or fewer main choices per screen. Users with impaired executive function cannot find the primary action in a field of equals.
`coga-content-usable/` 4.6.3 Avoid Too Much Content, 4.3.1 Make it Easy to Find the Most Important Tasks, 4.3.4 (key controls visible without scrolling).

**2.2 Make hierarchy legible from a distance, not from fine detail.**
"Minimal type size or type weight differences or color differences may not be perceived or understood. Hierarchy solely dependent on small unique design elements creates confusion."
`coga-content-usable/` 4.3.2 (How it Helps); `homeoffice-accessibility-posters/` screen-reader poster ("don't rely on text size and placement for structure").

**2.3 Use at most three or four emphasis levels on a screen, and assign each a single job.**
Label, placeholder, user data, and action must be distinguishable at a glance by more than one cue (weight, size, color, face). Dyslexic readers depend on bold rather than italics or caps for emphasis; everything at one level reads as a wall.
`bda-dyslexia-style-guide/` Highlighting ("bold rather than italics or underlining, avoiding long passages of capital letters"); `coga-content-usable/` 4.5.6 Use Clear Visible Labels; `practical-typography/` summary ("use bold or italic as little as possible"; all caps only under one line). **(synthesis)** The specific count of three or four is ours.

**2.4 Group by region: borders, background shading, or generous white space, with a heading.**
COGA's explicit guidance is to differentiate regions with "dividing lines, whitespace, and background colors" and to put a heading or icon on each. Flat designs where "chunks of content run into each other" lose all the benefit of chunking. Gestalt: proximity and common region beat line separators.
`coga-content-usable/` 4.3.3 Use a Clear and Understandable Page Structure, 4.2.6 (controls grouped with the content they affect, "clear dividers or white space between regions"); `ui-density/` "Design density" (proximity, similarity, figure-ground).

**2.5 Use white space as the first grouping tool; use lines only when space is not enough.**
White space "reduces clutter and provides definition," and it is the one grouping device that adds no ink. Each added rule is a design decision the reader must parse.
`coga-content-usable/` 4.4.10 Use White Spacing; `ui-density/` ("some whitespace has meaning almost as salient as the darker pixels"; count design decisions, not pixels); `bda-dyslexia-style-guide/` ("White space: reducing visual clutter").

**2.6 Put a control inside the region it affects, and label it to match the region's heading.**
Ambiguous scope ("does this button act on the section or the page?") causes some cognitively disabled users to stop entirely.
`coga-content-usable/` 4.2.6; `homeoffice-accessibility-posters/` low-vision poster ("put buttons and notifications in context", "don't separate actions from their context").

**2.7 Keep one scrolling region per screen.**
Nested or side-by-side scroll areas are called out repeatedly as a barrier.
`coga-content-usable/` 4.2.6 ("avoiding multiple or nested scrolling areas").

---

## 3. Density and disclosure

**3.1 Measure density as value per unit of attention, not items per square inch.**
Visual density is a snap judgement; the useful measure is value delivered divided by the time and space the UI occupies. A sparse screen that answers the question is denser than a packed one that does not.
`ui-density/` "Density in value" and Conclusion.

**3.2 Show only what is needed now; disclose the rest on request, in at most two levels.**
Progressive disclosure improves learnability, efficiency and error rate for novices and experts alike. Designs beyond two disclosure levels "typically have low usability because users often get lost."
`progressive-disclosure/` Benefits, Usability Criteria; `coga-content-usable/` 4.9.3 Support Simplification ("a simple application has 3 to 6 functions").

**3.3 Say each fact once per screen.**
Every repeated unit of information competes with the units that matter. Repeating the screen name, the entry type, or a count in several places is pure non-data ink.
`nielsen-10-heuristics/` #8; `ui-density/` "Information density" (Tufte's data-ink ratio: erase ink that does not present new information).

**3.4 Answer the writer's question before they ask it.**
Information software is "used" by looking. Infer context from history and environment (last scene opened, today's count) and present the answer; require interaction only to correct a wrong guess.
`magic-ink/` "Reducing interaction" (last-value prediction, relative navigation, tight feedback), "Showing the data".

**3.5 Overview first, then zoom and filter, then details on demand.**
For any collection (scenes, entries, notes), the first view is a scannable overview; details come on selection, not by default.
`eyes-have-it/` §2 Visual Information Seeking Mantra; §3 Tasks (overview, filter, details-on-demand).

**3.6 Keep critical paths short; move optional steps out of the path.**
Only required steps belong in a flow; "most used function" should be reachable in one or two actions from launch.
`coga-content-usable/` 4.6.2 Make Short Critical Paths.

---

## 4. Typography and readability

**4.1 Set body and prose text at 15-25px equivalent, 120-145% line spacing, 45-90 characters per line.**
These four choices determine most of the readability of a page; the writer stares at body text more than anything else.
`practical-typography/` "Typography in ten minutes", "Summary of key rules"; `wcag-22/` 1.4.8 Visual Presentation (80-character max, 1.5 line spacing, user-selectable colors); `web-design-95-typography/` ("treat text as a user interface").

**4.2 Never use italics for user-facing information; avoid all-caps runs longer than a few words.**
In an eye-tracking study of 48 dyslexic readers, italic faces significantly increased reading time and fixation duration; roman, sans-serif and monospaced faces shortened fixations. Dyslexia guidance also warns against capitals and underlining.
`good-fonts-dyslexia/` §5.1.2, §7 ("italic fonts decreased reading performance"); `bda-dyslexia-style-guide/` Highlighting; `homeoffice-accessibility-posters/` dyslexia poster ("don't underline words, use italics or write capitals"); `practical-typography/` ("all caps are fine for less than one line of text").

**4.3 Let the writer choose typeface, size, spacing and contrast; the defaults alone will not fit everyone.**
"No single format works for everyone with dyslexia. Individual preference and the ability to customise text are more important than following fixed formatting rules." Extra-large letter spacing roughly halved reading errors in dyslexic children, while standard spacing is optimal for skilled adults: only a setting serves both.
`bda-dyslexia-style-guide/` "Personalisation is best practice" (typeface, size, spacing, colour/contrast, line focus); `letter-spacing-dyslexia/` Results and Discussion; `wcag-22/` 1.4.12 Text Spacing (nothing may break when the user raises spacing); `coga-content-usable/` 4.9.4.

**4.4 Prefer plain sans-serif (or a clear mono) for UI text; reserve display faces for one title per screen.**
Sans serif and roman faces reduced fixation duration; Helvetica, Arial, Verdana, Courier and CMU performed well. Decorative or very light faces are not supported by the evidence.
`good-fonts-dyslexia/` §7; `bda-dyslexia-style-guide/` Typefaces; `coga-content-usable/` 4.9.4 (default fonts "clear or readable").

**4.5 Left-align; avoid centered and multi-column text; put text on plain backgrounds.**
`bda-dyslexia-style-guide/` Alignment, Backgrounds; `coga-content-usable/` 4.4.11 (foreground not obscured by background); `practical-typography/` ("use centered text sparingly").

**4.6 Consider softening pure black on white for long reading.**
Dark-blue-on-white or off-white is reported to reduce visual stress for some readers (this is about comfort, not dyslexia itself). Offer it, do not impose it.
`bda-dyslexia-style-guide/` Text contrast; `homeoffice-accessibility-posters/` dyslexia poster ("let users change the contrast").

**4.7 Write labels in plain, literal words; no abbreviations, idioms or invented jargon.**
Autistic and dyslexic users are both told the same thing from different directions: literal language, descriptive buttons, no figures of speech, no decoding of abbreviations ("page 4" not "p4").
`homeoffice-accessibility-posters/` autism poster; `coga-content-usable/` 4.4.1 Use Clear Words, 4.4.4 Use Literal Language, 4.4.6 (unambiguous formatting; avoid Roman numerals and unfamiliar symbols); `bda-dyslexia-style-guide/` Pagination; `nielsen-10-heuristics/` #2.

---

## 5. Attention and interruption

**5.1 Nothing moves, appears or changes unless the writer caused it.**
Content that shifts, auto-updates, or pops in causes mis-taps, disorientation and "cognitive overload and mental fatigue." This covers list reordering, banners, live counters, and any change on focus or input.
`coga-content-usable/` 4.5.1 Ensure Controls and Content Do Not Move Unexpectedly, 4.9.1 Let Users Control When Content Moves or Changes; `wcag-22/` 3.2.1 On Focus, 3.2.2 On Input, 3.2.5 Change on Request, 2.2.2 Pause Stop Hide.

**5.2 Batch and defer non-urgent system messages; let the writer choose when to see them.**
Sync status, update availability, and backup results are not emergencies. A setting controlling which notifications appear and how is the COGA model.
`coga-content-usable/` 4.6.1 (Use example: user chooses visual, sound, or none; essential contacts only); `interrupted-work-cost/` Discussion ("technology could be used to keep track of and control interruptions over a long period"); `wcag-22/` 2.2.4.

**5.3 Give every action immediate, visible, unambiguous feedback; never require a dialog to confirm success.**
"If the user clicks a checkbox and nothing happens, her action is rendered ambiguous." Feedback should be inline (state change, checkmark), not a modal, and should not itself be an interruption.
`magic-ink/` "Tight feedback loops"; `coga-content-usable/` 4.5.10 Provide Feedback; `nielsen-10-heuristics/` #1; `direct-manipulation/` §1 (rapid, incremental, reversible operations with immediately visible impact).

**5.4 Help the writer re-enter after a lapse: a clear heading says where they are and what they were doing.**
Headings, breadcrumbs and a visible current step let a distracted user "pick up where they left off" without re-reading. This is the one case where orientation signposting earns its ink.
`coga-content-usable/` 4.2.1 Make the Purpose of Your Page Clear, 4.2.4 Make Each Step Clear; `wcag-22/` 2.4.6 Headings and Labels, 2.4.8 Location.

**5.5 Never lose work; never time out; let the writer stop mid-task and come back.**
`coga-content-usable/` 4.5.9 Avoid Data Loss and Timeouts; `wcag-22/` 2.2.1, 2.2.6.

---

## 6. Consistency and predictability

**6.1 One shape, one meaning: identical components look identical everywhere, and things that look alike behave alike.**
Learning a new design pattern is the expensive part for cognitively disabled users; once learned it must hold across every screen. Six headings in two styles is COGA's example of what to avoid.
`coga-content-usable/` 4.2.3 Use a Consistent Visual Design, 4.2.5 Clearly Identify Controls; `wcag-22/` 3.2.3 Consistent Navigation, 3.2.4 Consistent Identification; `nielsen-10-heuristics/` #4.

**6.2 Prefer familiar platform patterns over invented ones; where a pattern is invented, make it discoverable.**
Users "cannot easily learn and remember new design metaphors." A control that is not obviously a control (underline-less link, tappable mono caption) fails for exactly the users we care about.
`coga-content-usable/` 4.2.2 Use a Familiar Hierarchy and Design, 4.2.5 (make borders of controls clear; non-clickable things must not look like controls); `nielsen-10-heuristics/` #4 (Jakob's Law).

**6.3 Put the same thing in the same place on every screen.**
"Consistently positioned where she can see them" is the autistic persona's first coping strategy; muscle memory removes the need to search.
`coga-content-usable/` 6.2.1 Amy Scenario 1, 4.3.3 ("positioning elements consistently will help users rely on muscle memory"); `wcag-22/` 3.2.6 Consistent Help.

**6.4 Make the state of every toggle, filter and mode visible at the place it applies.**
Recognition over recall: the writer should never have to remember that a filter is on or a mode is active.
`nielsen-10-heuristics/` #6; `coga-content-usable/` 4.5.10 (visible selected state), 4.7.5 (no recalling information across steps).

**6.5 Do not ask the writer to re-enter or remember anything the app already knows.**
`wcag-22/` 3.3.7 Redundant Entry; `coga-content-usable/` 4.7 Objective 6; `homeoffice-accessibility-posters/` dyslexia poster ("give reminders and prompts").

---

## 7. Motion

**7.1 Default to no motion in the writing surface; keep transitions elsewhere under 100 ms or make them cross-fades.**
Under 100 ms two states feel simultaneous and an animation can only make the app feel slower; between 100 ms and 1 s a transition bridges the gap. Movement near prose is a distractor by definition.
`ui-density/` "Density in time"; `perceptual-load-theory/` (salient moving stimuli are the distractors most easily perceived under low load).

**7.2 Provide a reduce-motion setting that disables every non-essential animation, and honor the OS setting.**
Flying banners, auto-scrolling content and animated transitions are named repeatedly as the thing that breaks focus for ADHD and autistic users.
`wcag-22/` 2.3.3 Animation from Interactions, 2.2.2 Pause Stop Hide; `coga-content-usable/` 6.10.2 Yuki (ADHD), 6.2.2 Amy (autism); `bda-dyslexia-style-guide/` ("avoid ... unnecessary animation").

**7.3 Never animate size; never animate layout the writer is reading.**
Reflowing a text column each frame stalls the editor and reads as the page moving under the cursor.
`coga-content-usable/` 4.5.1; **(synthesis)** the engineering corollary is already recorded in `DESIGN_README.md` ("snaps, never animates").

---

## 8. Control and personalization

**8.1 Offer a presentation settings surface: font, size, line and letter spacing, contrast/theme, motion, density, notifications.**
Personalization is the top recommendation of the dyslexia guide and a COGA objective. The defaults must still be good, but no default fits every reader.
`bda-dyslexia-style-guide/` five principles ("Make it adaptable"); `coga-content-usable/` 4.9 Objective 8, 4.9.4; `wcag-22/` 1.4.8, 1.4.12; `nielsen-10-heuristics/` #7 (allow users to tailor).

**8.2 Offer a simplified mode that hides non-essential chrome, and make it one step to get back.**
"Remove or hide features that most users do not use"; "make sure it is easy to get back to the full featured version." Focus mode is the seed of this.
`coga-content-usable/` 4.9.3 Support Simplification.

**8.3 Make undo universal and visible; make destructive actions reversible rather than confirmed.**
A clearly marked exit and reversibility lower anxiety more than confirmation dialogs do.
`nielsen-10-heuristics/` #3, #5; `coga-content-usable/` 4.5.2 Let Users Go Back; `direct-manipulation/` §1 (reversible operations reduce anxiety); `wcag-22/` 3.3.4.

**8.4 Support the writer's own structure, not an imposed regime.**
ADHD adults want tools that support self-given tasks and strengths, with flexible options, not behaviourist nudges. Streaks, goals and reminders must be opt-in and quiet.
`adhd-tech-neurodivergent-readers/` §6.3 ("supporting executive functioning on self-given tasks", "flexible options"), §6.1 (against behaviourist interventions).

**8.5 Let preferences be set once, in one obvious place, and apply everywhere.**
`coga-content-usable/` 4.9.1 More Details ("easily available": set once with the widest scope, reachable from each screen).

---

## 9. Research method

**9.1 Test with neurodivergent writers, not proxies and not simulations.**
Only about 12% of ADHD technology papers consulted people with ADHD; "testing systems on neurotypically presenting people cannot replace user tests" and simulating a disability is "misleading."
`adhd-tech-neurodivergent-readers/` §5.2-5.3, §6.2; `coga-content-usable/` §5.1 ("they are the experts in what works for them"); `microsoft-inclusive-design-toolkit/` "Learn from diversity" (p. 31); `bda-dyslexia-style-guide/` "Test with real users".

**9.2 Include a spread of conditions; one group's fix can be another's barrier.**
Fewer words and more numbers helps some autistic and dyslexic users and fails dyscalculic users; bright contrast helps low vision and hurts some autistic users.
`coga-content-usable/` §5.1; `homeoffice-accessibility-posters/` blog post ("where advice seems contradictory, it's always worth testing").

**9.3 Measure comprehension, errors, mood and frustration, not just task time or readability scores.**
`coga-content-usable/` §5.4 (efficacy, efficiency, satisfaction; "when does the user get frustrated or upset?"); `bda-dyslexia-style-guide/` ("Measuring comprehension, confidence and usability").

**9.4 Treat friction reports as data about the design, not about the user; use appreciative language.**
`adhd-tech-neurodivergent-readers/` §5.4.2, §6.1; `coga-content-usable/` §5.4.1 ("testers cannot do anything wrong").

**9.5 Decide the split between primary and secondary features from usage data and observation, then card-sort the secondary set.**
`progressive-disclosure/` Usability Criteria; `nielsen-10-heuristics/` #2 (user research uncovers terminology and mental models).

---

## Tensions and how Hammer resolves them

**T1. Tufte-style minimal ink vs. COGA's explicit grouping.**
Tufte (via `ui-density/`) says erase every mark that is not data; COGA 4.3.3 says draw borders, shade regions, add headings and icons. Both are right about different things: Tufte is about marks per fact; COGA is about marks per *group*. Resolution for Hammer: spend ink on group boundaries (a shaded region or clear white-space gutter, plus one heading) and remove ink from inside groups (repeated labels, counters, decorative rules). One strong boundary per group beats many weak lines. **(synthesis)**

**T2. Density for experienced writers vs. cognitive load.**
`ui-density/` argues software has become too sparse and expert users want more per screen; COGA says five or fewer choices and lots of white space. Resolution: density is a *setting* with a calm default (principle 8.1), and progressive disclosure gives experts the dense view on request (3.2). Density is also delivered through speed (temporal density, `ui-density/`) and through answering questions pre-emptively (3.4), which cost no attention. **(synthesis)**

**T3. Editorial character (mono greebles, roman numerals, § markers) vs. literal plain language.**
The manuscript aesthetic is a real asset: it is calm, consistent, and recognizable, and the maintainer wants to keep it. COGA 4.4.6 and the BDA guide warn against Roman numerals, abbreviations and symbols the reader must decode. Resolution: greebles stay where they carry a fact the writer uses (word count, last edit, sync state) and where there is no plain-language equivalent nearby; they go where they duplicate the heading, the breadcrumb, or each other. Numerals and abbreviations get a plain-text twin or are replaced ("Chapter 2" not "§ II"; "3 scenes" not "3 SCN"). **(synthesis)**

**T4. Hairlines-not-elevation vs. perceivable grouping.**
A single hairline per section is almost zero ink, which is good, but COGA 4.3.2 warns that hierarchy from "minimal ... differences" is not perceived. Resolution: keep the hairline vocabulary as the *texture*, and add one of two stronger grouping cues per section: a surface-tone region or a larger white-space gutter. The rule is the accent, not the only boundary. **(synthesis)**

**T5. Immediate feedback vs. no motion.**
Feedback should be instant and visible (5.3) but nothing should move (5.1, 7.1). Resolution: feedback is a state change in place (fill, check, label), never a translation or size animation; short cross-fades only. **(synthesis)**

**T6. Recognizable controls vs. chrome that recedes.**
COGA wants buttons that look like buttons; Hammer wants chrome to disappear. Resolution: controls that recede while the writer is typing (collapse-while-typing is already this) but are unambiguously controls when visible: bordered, labeled with a verb, in a predictable position. Never a bare mono caption as the only affordance. **(synthesis)**

---

## Screen review checklist

Apply to each screen, in both phone and desktop widths, light and dark.

1. **One primary thing.** Can you name the single most important element, and is it visibly the largest or strongest? (2.1)
2. **Count the emphasis levels.** Label, placeholder, user data, action: can a stranger tell them apart at arm's length? Three or four levels at most. (2.3)
3. **Each fact once.** Is the screen name, the item type, or any count shown more than once? Remove the copy that is farther from where it is used. (3.3)
4. **Groups have a boundary and a heading.** Is each group bounded by shade or clear space, not only by a line? Count the lines; if lines outnumber groups, remove lines. (2.4, 2.5, T1, T4)
5. **Controls sit in their scope.** Is every button inside the region it changes and labeled with a verb that matches the heading? (2.6)
6. **Plain words.** Any abbreviations, numerals, symbols or idioms the writer must decode? Give them a plain twin or drop them. (4.7, T3)
7. **No italics, no long caps.** (4.2)
8. **Nothing moves uninvited.** Does anything appear, scroll, reorder or animate without a user action? Does any animation change size or layout? (5.1, 7.1, 7.3)
9. **Interruptions are opt-in and deferrable.** Can every banner or toast be postponed or silenced from settings? (1.4, 5.2)
10. **Re-entry.** If the writer looks away for five minutes, does the heading tell them where they are and what state they left? (5.4)
11. **Disclosure depth.** Is anything more than two levels deep? Is the secondary set behind one clearly labeled control? (3.2)
12. **Feedback in place.** Does each action change something visible where the action happened? (5.3)
13. **Consistency.** Same component, same look, same position as the other screens? (6.1, 6.3)
14. **Personalization respected.** Does the screen survive 200% text, 1.5x line height, wider letter spacing, high contrast, reduced motion? (4.3, 8.1)
15. **Scroll.** One scrolling region. (2.7)
16. **Touch targets.** 44dp preferred, 24dp minimum, and not bunched. (`wcag-22/` 2.5.5, 2.5.8; `homeoffice-accessibility-posters/` motor poster)
