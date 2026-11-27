# Keyboard-first navigation and shortcut system (4.0)

## Context

Users keep asking for shortcuts (latest: Darkrock-Studios/hammer-editor#1118, a Discord user remapping keys with AutoHotkey). Today shortcuts are scattered: each project shortcut is wired in five places (ProjectShortcutHost, ProjectRootUi, desktop `WindowShortcuts`, Android `dispatchKeyEvent`, iOS Swift `keyCommands`), nothing shows a chord in the UI, and the scene tree, entry lists and nav rail have no keyboard handling at all.

Goal: "HOTAS" navigation. Someone with both hands on the keyboard can drive a whole project without the mouse. Esc already backs out of things; we build a ladder around it.

**In scope (4.0):** central action table on all platforms, module jumps, pane cycling, keyboard-driven tree and lists, Esc ladder, context action chords, F1 cheat sheet, Ctrl+K command palette, chords shown in menus and tooltips.
**Out of scope:** user rebinding, shortcut schemes, Vim mode. Design so rebinding later is just an override of a `Map<ActionId, List<KeyChord>>`.
**Anti-goals:** a stray Esc mid-sentence closing a scene; stealing editor chords (selection, formatting) or macOS caret keys; Alt chords (AltGr = Ctrl+Alt, issue #859).

## Decisions

- Scope: Core + palette. Rebinding later.
- Esc ladder: first Esc in an editor moves focus to the tree or list with that item highlighted (item stays open); second Esc closes it; further Esc climbs as today.
- Approach A: one action table in `common`; each platform host matches chords **before** Compose focus and routes to handlers registered by whoever can perform the action while alive. Arrows and Enter stay focus-local. iOS `UIKeyCommand`s are generated from the table.
- Chords use **Cmd on macOS/iOS, Ctrl elsewhere** (keeps Cocoa Control+N/P/K caret moves). This replaces the "Ctrl and Cmd interchangeable" rule for table chords.
- **Ctrl+W closes the current item; Ctrl+Shift+W closes the project.** Changelog entry needed.
- Can be cut if short on time: keyboard scene reorder, timeline next/prev event.
- Approaches rejected: B (table as labels only, keys along focus path) fails when nothing is focused, which is the normal state today; C (iOS hand-written) drifts.

## Architecture

New package `common/.../components/actions/` (component tier UI coordination; not Foundation, not Koin; avoid Konsist suffixes):
- `ChordKey` enum, `KeyChord(key, primary, shift)` (no `alt` field, so Alt chords are unrepresentable).
- `ActionId` value class, `ActionDef(id, label: StringResource, category, defaultChords, global, dispatch = Host | Local)`.
- `HammerActions` table; `Keymap` built from defaults.
- `ActionRegistry` (implements `ActionRegistrar` + `ActionDispatcher`): `register(layer, handlers)`, `dispatch(chord)`, `invoke(id)`, `live()`. Helper `registerWhileStarted(lifecycle, ...)`.
- Owned by `ProjectRootComponent`, threaded by constructor through `ProjectRootRouter`, **replacing the unused `addMenu`/`removeMenu` pair** that already reaches every module component.
- Components register domain actions (testable in common desktopTest). UI registers only focus/host actions via `RegisterActions(...)` `DisposableEffect` and `LocalActionRegistrar` provided in `ProjectRootUi`.

Precedence: `ActionLayer { Root, Module, Item, Overlay }`; highest layer wins, then most recent. Overlay registrations can set `blocksBelow`, hiding lower handlers except `global` actions (palette, cheat sheet, save all, sync, quit). This stops Ctrl+N in focus mode creating a scene behind it. Unmatched chords return false and pass to the focused widget.

## Default chords ("Mod" = Cmd on macOS/iOS, Ctrl elsewhere)

| Action | Chord |
|---|---|
| nav.home / story / notes / encyclopedia / timeline | Mod+1..5 |
| pane.next / pane.prev | F6 / Shift+F6 |
| item.new (scene, note, entry, event by module) | Mod+N |
| story.newGroup | Mod+Shift+N |
| item.next / item.prev | Mod+PageDown / Mod+PageUp |
| story.nextGroup / prevGroup | Mod+Shift+PageDown / PageUp |
| story.toggleOutline | Mod+Shift+O |
| story.toggleSceneInfo | Mod+Shift+M |
| story.focusMode | Mod+Shift+Enter |
| story.readingMode | Mod+Shift+R |
| story.newDraft / viewDrafts | Mod+Shift+D / Mod+Shift+H |
| item.close / project.close | Mod+W / Mod+Shift+W |
| palette.open / help.cheatSheet | Mod+K / F1 |
| migrated: search.global, project.saveAll, project.sync, app.quit | Mod+Shift+F, Mod+Shift+S, F3, Mod+Q |
| Local (display only, stay focus modifiers) | Mod+S, Mod+F, Mod+B/I, Mod+Shift+X/I, Mod+= / - / 0 |

Next/prev uses PageUp/PageDown because Ctrl+Shift+Up/Down is the editor's paragraph selection. `ReservedChordsTest` enforces: no collision with composetexteditor defaults or Hammer chords, no Alt, no duplicates.

The editor defaults above were read from composetexteditor 3.2.0. Branch `4_0/texteditor-3.5.0` upgrades it; recheck `KeyBindings` when that lands and keep `ReservedChordsTest`'s editor list in sync.

## Key mechanics

- **Esc ladder:** Esc stays out of the table (KeyUp on window `onKeyEvent`). `Modifier.escapeToPane` on the Story detail pane acts on KeyUp only if it saw the KeyDown ("armed"), so a dialog that dismissed on KeyDown doesn't leak a ladder step; same arming for window Back. Armed + multi-pane + focus in editor: put tree cursor on the open scene, expand ancestors, focus tree, consume. Otherwise bubble to `backDispatcher.back()`. Single-pane modules: first Esc moves focus out of the text field (edit mode kept), next Esc is today's back, then list with cursor restored, then Home.
- **Pane focus:** `composeUi/.../compose/focus/PaneFocus.kt`, `Modifier.focusPane(...)` (FocusRequester + focusGroup + focusRestorer + onFocusChanged). Pane order computed in composition. On destination change in keyboard input mode, focus the primary pane.
- **Tree/lists:** roving cursor; the list container is the one focus stop, rows not focusable (LazyColumn recycling). Shared `KeyboardListNavigation.kt` (Up/Down/Home/End/Enter, scroll into view). `SceneTreeState` gains cursor + Left/Right collapse/expand/parent. Applied to `BrowseNotesUi`, `BrowseEntriesUi`. Timeline canvas gets no arrow nav.
- **Palette/cheat sheet:** second root slot `commandOverlay` (Palette, CheatSheet) separate from the modal slot so it opens over focus mode. `CommandPaletteComponent` snapshots `live()`, pure `rankActions` fuzzy filter, `run(id)` dismisses then invokes. UI extends `HdSearchableListDialog` with keyboard selection and auto-focus (GlobalSearch FocusRequester pattern). Cheat sheet: `HdHairlineDialogShell` + `HdSectionHeader` per category, dead actions dimmed.
- **Hosts:** desktop window `onPreviewKeyEvent` → `dispatch(event.toKeyChord())` (`KeyChordCompose.kt`, letters on `layoutKey`, exact modifiers, Alt returns null); Android pure `androidChord(keyCode, metaState)`; iOS pure `iosKeyCommandSpecs(keymap)` consumed by Swift, `canPerformAction` checks liveness. Delete `ProjectShortcutHost`, `WindowShortcuts.ProjectShortcut`, dead `EditorMenuBar`, `ApplicationState.menu`, `KeyShortcut`.
- **Menus/tooltips:** `MenuItemDescriptor.shortcut` becomes `actionId`; `TopAppBarDropdownMenu`, `DetailViewDropdownMenu` show chord as trailing `HdMonoLabel` on desktop or keyboard input mode; `MarkdownFormatBar` tooltips add chords.

## Checkpoints (each: tests green, commit, `/code-review`, fix findings)

1. Core + desktop host: actions package, registry, table, `toKeyChord`; nav.* and migrated actions. **Spike first:** does the window `onPreviewKeyEvent` see keys while a Compose `Dialog` (focus mode) has focus? If not, dialog shells also dispatch. Tests: `ActionRegistryTest`, `ReservedChordsTest`, `KeyChordComposeTest` (AltGr null, Mod per platform, AZERTY digits with NumPad aliases), `ProjectRootComponentTest` (Mod+3 shows Notes).
2. Android + iOS hosts; delete old host code. Tests: `iosKeyCommandSpecsTest`, `androidChord` test.
3. Story context actions; replace `addMenu`/`removeMenu`; move SceneList create-dialog state into component; next/prev scene and group. Tests in SceneList/SceneEditor/StoryEditor component tests.
4. Notes, Encyclopedia, Timeline: new, next/prev, close (Mod+W). Dropdown menus show chords.
5. Overlay barriers on dialog shells and modal components. Test: Mod+N with focus mode open does nothing.
6. Palette + cheat sheet. Tests: `CommandPaletteComponentTest`, `FuzzyRankTest`, composeUi `CommandPaletteUiTest`.
7. Pane focus, F6, tree and list keyboard nav. composeUi tests: `SceneTreeKeyboardTest`, `PaneCycleTest`, `BrowseNotesKeyboardTest`.
8. Esc ladder (`EscLadderTest`), tooltips, rewrite `docs/KEYBOARD-SHORTCUTS.md` (add text-size chords, ladder, F6, Mac Cmd rule; test that every table id is documented), CHANGELOG (Ctrl+W change).

## Critical files

- `common/.../components/projectroot/ProjectRootComponent.kt`, `ProjectRootRouter.kt`
- `desktop/.../ProjectEditorWindow.kt`, `WindowShortcuts.kt`
- `android/.../ProjectRootActivity.kt`, `ios/ios/ComposeContainer.swift`
- `composeUi/.../compose/ProjectShortcutHost.kt`, `ShortcutModifiers.kt`, `projectroot/ProjectRootUi.kt`
- `composeUi/.../storyeditor/StoryEditorUi.kt`, `scenelist/scenetree/SceneTreeState.kt`, `SceneTree.kt`
- `common/.../data/MenuDescriptor.kt`, `TopAppBarDropdownMenu.kt`

## Verification

- `./gradlew desktopTest` and composeUi desktop tests per checkpoint.
- Run `./gradlew :desktop:run --args='--dev'` with a demo project and drive it mouse-free: Mod+1..5, F6, arrows/Enter in tree, Esc ladder from an open scene, Mod+N in each module, Mod+K palette over focus mode, F1.
- Android with a hardware keyboard (emulator) via `/android-cli`: module jumps, save all, sync.
- iOS keyCommands checked via spec test; manual check on simulator if available.

## Execution

Branch `feature/keyboard-navigation` off `4.0.0`; PR targets `4.0.0`. Implement/review loop per checkpoint.
