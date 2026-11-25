# Keyboard Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One central action table drives every project shortcut on desktop, Android and iOS, so a writer can drive a whole project from the keyboard: module jumps, pane cycling, tree and list navigation, an Esc ladder, context chords, an F1 cheat sheet and a Mod+K command palette.

**Architecture:** A new `components/actions` package in `common` holds the chord types, the `HammerActions` table, a `Keymap` and an `ActionRegistry` owned by `ProjectRootComponent`. Components register domain handlers per lifecycle (`registerWhileStarted`); the UI registers only focus and host handlers. Each platform host turns a key event into a `KeyChord` before Compose focus and calls `dispatch`; layered precedence with overlay barriers decides who answers.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Decompose/Essenty, compose resources (`Res.string`), composetexteditor 3.5.1 (`layoutKey`), JUnit 5, MockK only where the existing test fixture already uses it.

**Spec:** `docs/superpowers/specs/2026-11-24-keyboard-navigation-design.md`

## Global Constraints

- No Alt chords. `KeyChord` has no `alt` field; `toKeyChord` and `androidChord` return null whenever Alt (AltGr arrives as Ctrl+Alt, issue #859) is held.
- Exact modifier match: an extra modifier means no chord.
- "Mod" = Cmd on macOS and iOS, Ctrl on Windows, Linux and Android. On Apple platforms a held Ctrl, and elsewhere a held Meta/Windows key, means no chord (keeps Cocoa Control+N/P/K caret moves).
- Letters match on composetexteditor's `KeyEvent.layoutKey`; digits match the digit row and the NumPad digits.
- Mod+W = `item.close`; Mod+Shift+W = `project.close`. CHANGELOG entry required (Task 8.3).
- Arrows, Enter and Esc stay focus-local; Esc is never in the table.
- Local actions (Mod+S, Mod+F, Mod+B, Mod+I, Mod+Shift+X, Mod+Shift+I, Mod+= / Mod+Shift+=, Mod+-, Mod+0) are display-only: the host never dispatches them; their focus modifiers stay.
- No rebinding UI. `Keymap` is built from table defaults; later rebinding is an override of `Map<ActionId, List<KeyChord>>`.
- 4.0 breaking changes are fine: delete dead code (`addMenu`/`removeMenu`, `MenuDescriptor`, `KeyShortcut`, `ProjectShortcutHost`, `EditorMenuBar`, `ApplicationState.menu`, `shortcutHint`, the Android `TopAppBarDropdownMenu`) rather than shim it.
- No persisted settings change is planned. If one appears, CLAUDE.md applies: one `*Datasource` owner per file format.
- The `actions` package is component-tier UI coordination: no Koin, no Konsist layer suffixes (`Repository`, `Service`, `UseCase`, `Datasource`, `Store`, `Journal`, `Allocator`). Any `*Component` class lives under a `components` package (`NamingTest`).
- `ActionRegistry` is main-thread only (components register from lifecycle callbacks, UI from effects).
- Action label strings: English only, in `common/src/commonMain/composeResources/values/strings_actions.xml`. Other locales (`values-de`, `values-fr`, ...) already miss whole files and fall back to English; Crowdin picks the new file up.
- Text style everywhere (code, comments, docs, commits): no em dashes; comments only for non-obvious things; no change-history comments; no mention of Claude in commits or PRs.
- Tests: classical school, behavior over implementation, AAA, fakes over mocks for new code. Common tests in `common/src/desktopTest/kotlin/` (package by folder, e.g. `components.actions`), composeUi tests in `composeUi/src/desktopTest/kotlin/` (JUnit 5 with `runComposeUiTest`, as `MatchesShortcutTest`).
- Implementer agents never commit. The supervisor commits at the end of each checkpoint, on `feature/keyboard-navigation` (base `4.0.0`; PR targets `4.0.0`).

## Review Focus

1. **AltGr layouts** (Polish, German, Turkish on Windows send Ctrl+Alt+key): typing `@`, `ś`, `€` must type the character, never fire Mod+key. Pinned in Task 1.3 (`AltGr chords are never actions`) and Task 2.1 (`alt chords are not actions`).
2. **AZERTY digits**: the digit row types `& é " ' (` unshifted; on Windows and macOS the key still reports `Key.One`, on X11 it may report the symbol. Mod+1..5 must work from the NumPad, and Ctrl+apostrophe on QWERTY must not become Mod+4. Pinned in Task 1.3 (`numpad digits alias the digit row`, `the apostrophe key is not a digit`) and Task 2.1 (`lock keys do not block chords`).
3. **Esc KeyUp after a dialog dismissed on KeyDown**: closing a dialog with Esc must not also step the ladder or navigate back. Pinned in Task 8.1 (`an Esc release without its press does nothing`, `a dialog dismissed by Esc does not also step back`).
4. **Palette over focus mode**: Mod+K opens the palette on top of focus mode without closing it; the palette offers only what is live under the focus mode barrier, and running a global action works. Pinned in Task 6.2 (`Mod+K over focus mode opens the palette and keeps focus mode`).
5. **Mod+W on an item with unsaved edits**: must raise the existing unsaved-changes confirm, not close. Pinned in Task 4.1 (`Mod+W on a dirty edit asks before closing`), Task 4.2 (`Mod+W on a dirty entry edit asks before closing`) and Task 4.3 (`Mod+W on a dirty event edit asks before closing`).

## Where the code differs from the spec

- composetexteditor is already 3.5.1 on `4.0.0` (`gradle/libs.versions.toml:115`). `ReservedChordsTest` lists the 3.5.1 `CtrlKeyBindings`/`MacKeyBindings` chords.
- `addMenu`/`removeMenu` and `MenuDescriptor` are dead (every host passes no-ops), but `MenuItemDescriptor` is live: components publish `state.menuItems` that `TopAppBarDropdownMenu`/`DetailViewDropdownMenu` render. Keep `MenuItemDescriptor`; delete the rest.
- The images and sync menus in `ProjectRootComponent` and the "new timeline" menu in `TimeLineOverviewComponent` exist only through `addMenu`; they are deleted. Sync becomes `project.sync`.
- `MarkdownFormatBar` tooltips already show chords through `shortcutHint` (`composeUi/.../compose/markdowneditor/ShortcutHint.kt`). Task 8.2 replaces that with keymap labels and deletes `shortcutHint`.
- `ViewEntryUi` holds the entry's edit buffer and decides dirtiness (`ViewEntryUi.kt:309-321`), so entry `item.close` is registered from the UI (Task 4.2).
- `BrowseNotesUi` filters and sorts notes in the UI (`BrowseNotesUi.kt:154-176`), so notes next/prev follows the order the list reports (Task 4.1).
- The nav rail sits outside `ProjectRootUi` on desktop (`ProjectEditorWindow.kt:273-285`); `LocalActionRegistrar` is provided in `ProjectRootUi` as the spec says, so F6 cycles content panes only (Mod+1..5 replaces rail navigation).
- composetexteditor-find binds F3 to find-next, but Hammer uses its own `findShortcutModifier`, so `project.sync` keeps F3.
- The editor treats PageUp/PageDown as page motions whatever modifier is held; Mod+PageUp/PageDown in the table pre-empt that inside the editor, which is intended.
- `ProjectEditorWindow.kt:59` creates `BackDispatcher()` without `remember`, so after any recomposition the window's Esc talks to a dispatcher the component never saw. Fixed in Task 1.5.

---

## Checkpoint 1: Core and desktop host

### Task 1.0: Spike, does the window see keys while a Compose `Dialog` has focus?

**Files:**
- Modify (temporary, reverted at the end of the task): `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/ProjectEditorWindow.kt:96-124`

- [ ] **Step 1: Add temporary logging.** In `onPreviewKeyEvent` add `Napier.d("SPIKE preview ${event.type} ${event.key}")` before the `when`; in `onKeyEvent` add `Napier.d("SPIKE key ${event.type} ${event.key}")`.
- [ ] **Step 2: Supervisor runs** `./gradlew :desktop:run --args='--dev'`, opens a demo project, opens a scene, enters focus mode (scene editor menu, Focus mode), presses F3 and Esc, then opens and Esc-dismisses the outline overview. If the supervisor cannot drive a GUI, ask Adam to do it and paste the log.
- [ ] **Step 3: Record the outcome** in the checkpoint commit message body as one line each:
  - `Window preview sees keys inside Dialog: yes|no`
  - `Window onKeyEvent sees the Esc KeyUp after an Esc-dismissed dialog: yes|no`
- [ ] **Step 4: Revert the logging.**

Outcome handling:
- **Yes (window preview sees dialog keys):** hosts stay window-only. Task 5.2 adds only the barrier.
- **No:** Task 5.2 also makes `AnimatedDialogContainer` (`composeUi/.../compose/AnimatedFullScreenDialog.kt:96-121`, the only raw `Dialog` in composeUi) call the dispatcher from its content root: `Modifier.onPreviewKeyEvent { e -> e.toKeyChord()?.let { LocalActionDispatcher.current?.dispatch(it) } ?: false }`. That covers focus mode, the palette and every animated dialog.
- The Esc answer does not change the design (arming is built either way); it tells the Task 8.1 reviewer which path the leak takes on this platform.

### Task 1.1: Chords, action table, keymap, strings

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ChordKey.kt`
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ChordPlatform.kt`
- Create: `common/src/desktopMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ChordPlatform.desktop.kt`
- Create: `common/src/androidMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ChordPlatform.android.kt`
- Create: `common/src/iosMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ChordPlatform.ios.kt`
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ActionDef.kt`
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/HammerActions.kt`
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/Keymap.kt`
- Create: `common/src/commonMain/composeResources/values/strings_actions.xml`
- Test: `common/src/desktopTest/kotlin/components/actions/ReservedChordsTest.kt`
- Test: `common/src/desktopTest/kotlin/components/actions/KeyChordLabelTest.kt`

**Interfaces:**
- Produces (package `com.darkrockstudios.apps.hammer.common.components.actions`):

```kotlin
enum class ChordKey(val display: String) {
	A("A"), /* ...through */ Z("Z"),
	Digit0("0"), /* ...through */ Digit9("9"),
	F1("F1"), /* ...through */ F12("F12"),
	PageUp("PgUp"), PageDown("PgDn"), Enter("Enter"), Equals("="), Minus("-"),
}

data class KeyChord(val key: ChordKey, val primary: Boolean = false, val shift: Boolean = false) {
	fun label(platform: ChordPlatform = currentChordPlatform): String
	companion object {
		fun mod(key: ChordKey, shift: Boolean = false): KeyChord
		fun bare(key: ChordKey, shift: Boolean = false): KeyChord
	}
}

enum class ChordPlatform(val modIsCmd: Boolean, val showsChordsByDefault: Boolean) {
	DesktopMac(modIsCmd = true, showsChordsByDefault = true),
	Desktop(modIsCmd = false, showsChordsByDefault = true),
	Ios(modIsCmd = true, showsChordsByDefault = false),
	Android(modIsCmd = false, showsChordsByDefault = false),
}
expect val currentChordPlatform: ChordPlatform

@JvmInline value class ActionId(val value: String)
enum class ActionCategory(val label: StringResource) { Navigation, Items, Story, Project, Editing, Help }
enum class ActionDispatch { Host, Local }
enum class ActionLayer { Root, Module, Item, Overlay }   // ordinal is precedence
data class ActionDef(
	val id: ActionId,
	val label: StringResource,
	val category: ActionCategory,
	val defaultChords: List<KeyChord>,
	val global: Boolean = false,
	val dispatch: ActionDispatch = ActionDispatch.Host,
)

object HammerActions {
	val NavHome: ActionDef; val NavStory: ActionDef; val NavNotes: ActionDef
	val NavEncyclopedia: ActionDef; val NavTimeline: ActionDef
	val PaneNext: ActionDef; val PanePrev: ActionDef
	val ItemNew: ActionDef; val ItemNext: ActionDef; val ItemPrev: ActionDef; val ItemClose: ActionDef
	val StoryNewGroup: ActionDef; val StoryNextGroup: ActionDef; val StoryPrevGroup: ActionDef
	val StoryToggleOutline: ActionDef; val StoryToggleSceneInfo: ActionDef
	val StoryFocusMode: ActionDef; val StoryReadingMode: ActionDef
	val StoryNewDraft: ActionDef; val StoryViewDrafts: ActionDef
	val ProjectClose: ActionDef; val ProjectSaveAll: ActionDef; val ProjectSync: ActionDef
	val SearchGlobal: ActionDef; val PaletteOpen: ActionDef; val HelpCheatSheet: ActionDef; val AppQuit: ActionDef
	val EditSave: ActionDef; val EditFind: ActionDef; val EditBold: ActionDef; val EditItalic: ActionDef
	val EditStrikethrough: ActionDef; val EditInsertImage: ActionDef
	val EditTextBigger: ActionDef; val EditTextSmaller: ActionDef; val EditTextReset: ActionDef
	val all: List<ActionDef>              // table order = cheat sheet and palette order
	fun byId(id: ActionId): ActionDef?
}

class Keymap(private val chords: Map<ActionId, List<KeyChord>>) {
	fun chordsFor(id: ActionId): List<KeyChord>
	fun primaryChord(id: ActionId): KeyChord?
	fun actionFor(chord: KeyChord): ActionId?
	companion object {
		fun defaults(): Keymap
		val Default: Keymap
	}
}
```

Table values (id, category, chords, flags). Ids are the strings in the spec:

| Val | id | Category | Default chords | Flags |
|---|---|---|---|---|
| NavHome..NavTimeline | `nav.home`, `nav.story`, `nav.notes`, `nav.encyclopedia`, `nav.timeline` | Navigation | Mod+1 .. Mod+5 | |
| PaneNext / PanePrev | `pane.next` / `pane.prev` | Navigation | F6 / Shift+F6 | |
| ItemNew | `item.new` | Items | Mod+N | |
| ItemNext / ItemPrev | `item.next` / `item.prev` | Items | Mod+PageDown / Mod+PageUp | |
| ItemClose | `item.close` | Items | Mod+W | |
| StoryNewGroup | `story.newGroup` | Story | Mod+Shift+N | |
| StoryNextGroup / StoryPrevGroup | `story.nextGroup` / `story.prevGroup` | Story | Mod+Shift+PageDown / Mod+Shift+PageUp | |
| StoryToggleOutline | `story.toggleOutline` | Story | Mod+Shift+O | |
| StoryToggleSceneInfo | `story.toggleSceneInfo` | Story | Mod+Shift+M | |
| StoryFocusMode | `story.focusMode` | Story | Mod+Shift+Enter | |
| StoryReadingMode | `story.readingMode` | Story | Mod+Shift+R | |
| StoryNewDraft / StoryViewDrafts | `story.newDraft` / `story.viewDrafts` | Story | Mod+Shift+D / Mod+Shift+H | |
| ProjectClose | `project.close` | Project | Mod+Shift+W | |
| ProjectSaveAll | `project.saveAll` | Project | Mod+Shift+S | global |
| ProjectSync | `project.sync` | Project | F3 | global |
| SearchGlobal | `search.global` | Project | Mod+Shift+F | |
| PaletteOpen | `palette.open` | Help | Mod+K | global |
| HelpCheatSheet | `help.cheatSheet` | Help | F1 | global |
| AppQuit | `app.quit` | Project | Mod+Q | global |
| EditSave, EditFind, EditBold, EditItalic | `edit.save`, `edit.find`, `edit.bold`, `edit.italic` | Editing | Mod+S, Mod+F, Mod+B, Mod+I | Local |
| EditStrikethrough, EditInsertImage | `edit.strikethrough`, `edit.insertImage` | Editing | Mod+Shift+X, Mod+Shift+I | Local |
| EditTextBigger, EditTextSmaller, EditTextReset | `edit.textBigger`, `edit.textSmaller`, `edit.textReset` | Editing | [Mod+=, Mod+Shift+=], Mod+-, Mod+0 | Local |

String keys (file `strings_actions.xml`, `<resources>` like `strings_project_navigation.xml`): `action_category_navigation` Navigation, `action_category_items` Items, `action_category_story` Story, `action_category_project` Project, `action_category_editing` Editing, `action_category_help` Help; one label per action named `action_` + id with dots and camel case turned into underscores: `action_nav_home` "Go to Home", `action_nav_story` "Go to Story", `action_nav_notes` "Go to Notes", `action_nav_encyclopedia` "Go to Encyclopedia", `action_nav_timeline` "Go to Timeline", `action_pane_next` "Next pane", `action_pane_prev` "Previous pane", `action_item_new` "New item", `action_item_next` "Next item", `action_item_prev` "Previous item", `action_item_close` "Close item", `action_story_new_group` "New group", `action_story_next_group` "Next group", `action_story_prev_group` "Previous group", `action_story_toggle_outline` "Outline overview", `action_story_toggle_scene_info` "Scene info", `action_story_focus_mode` "Focus mode", `action_story_reading_mode` "Reading mode", `action_story_new_draft` "Save as draft", `action_story_view_drafts` "View drafts", `action_project_close` "Close project", `action_project_save_all` "Save all", `action_project_sync` "Sync project", `action_search_global` "Search project", `action_palette_open` "Command palette", `action_help_cheat_sheet` "Keyboard shortcuts", `action_app_quit` "Quit Hammer", `action_edit_save` "Save", `action_edit_find` "Find", `action_edit_bold` "Bold", `action_edit_italic` "Italic", `action_edit_strikethrough` "Strikethrough", `action_edit_insert_image` "Insert image", `action_edit_text_bigger` "Larger text", `action_edit_text_smaller` "Smaller text", `action_edit_text_reset` "Reset text size".

- [ ] **Step 1: Write the failing tests.**

`KeyChordLabelTest`:

```kotlin
@Test fun `Apple labels use symbols`() {
	assertEquals("⌘⇧N", KeyChord.mod(ChordKey.N, shift = true).label(ChordPlatform.DesktopMac))
	assertEquals("⌘↩", KeyChord.mod(ChordKey.Enter).label(ChordPlatform.Ios))
}
@Test fun `other labels spell the modifiers`() {
	assertEquals("Ctrl+Shift+N", KeyChord.mod(ChordKey.N, shift = true).label(ChordPlatform.Desktop))
	assertEquals("Shift+F6", KeyChord.bare(ChordKey.F6, shift = true).label(ChordPlatform.Android))
	assertEquals("Ctrl+PgDn", KeyChord.mod(ChordKey.PageDown).label(ChordPlatform.Desktop))
}
```

`ReservedChordsTest` (the editor list is composetexteditor 3.5.1 `CtrlKeyBindings` and `MacKeyBindings`; keep it in sync on every texteditor upgrade):

```kotlin
private val editorChords = listOf(
	ChordKey.A, ChordKey.C, ChordKey.X, ChordKey.B, ChordKey.I, ChordKey.U,
	ChordKey.E, ChordKey.V, ChordKey.Y, ChordKey.Z,
).map { KeyChord.mod(it) } + listOf(ChordKey.X, ChordKey.V, ChordKey.Z).map { KeyChord.mod(it, shift = true) } +
	listOf(KeyChord.bare(ChordKey.PageUp), KeyChord.bare(ChordKey.PageDown),
		KeyChord.bare(ChordKey.Enter), KeyChord.bare(ChordKey.Enter, shift = true))

private val hostChords = HammerActions.all.filter { it.dispatch == ActionDispatch.Host }.flatMap { it.defaultChords }

@Test fun `no two actions share a chord`() {
	val all = HammerActions.all.flatMap { def -> def.defaultChords.map { it to def.id } }
	assertEquals(emptyMap(), all.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 })
}
@Test fun `host chords avoid the text editor's own chords`() =
	assertEquals(emptyList(), hostChords.filter { it in editorChords })
@Test fun `host chords need Mod or a function key`() =
	assertEquals(emptyList(), hostChords.filter { !it.primary && it.key !in functionKeys })
@Test fun `the default keymap answers every table chord with its action`() =
	HammerActions.all.forEach { def -> def.defaultChords.forEach { assertEquals(def.id, Keymap.Default.actionFor(it)) } }
@Test fun `ids are unique`() = assertEquals(HammerActions.all.size, HammerActions.all.map { it.id }.toSet().size)
```

`functionKeys` = `ChordKey.F1..F12`.

- [ ] **Step 2: Run, expect compile failure.** `./gradlew :common:desktopTest --tests 'components.actions.*'`
- [ ] **Step 3: Implement the types and table in the files above.** `currentChordPlatform` actuals: desktop `if (hostOs == HostOs.MacOs) ChordPlatform.DesktopMac else ChordPlatform.Desktop` (`common/src/desktopMain/.../common/HostOs.kt`); android `ChordPlatform.Android`; ios `ChordPlatform.Ios`. Actuals go in `desktopMain` and `androidMain`, not the shared `jvmMain`, as `NuxPlatform` does. Apple label: `⌘` if primary, `⇧` if shift, then the key (`↩` for Enter). Other label: `Ctrl`, `Shift`, key joined by `+`.
- [ ] **Step 4: Run the tests, expect PASS.** Same command.

### Task 1.2: ActionRegistry and lifecycle registration

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/ActionRegistry.kt`
- Test: `common/src/desktopTest/kotlin/components/actions/ActionRegistryTest.kt`

**Interfaces:**
- Consumes: Task 1.1 types.
- Produces:

```kotlin
class ActionHandler(val isEnabled: () -> Boolean = { true }, val perform: () -> Unit)
fun interface ActionRegistration { fun unregister() }

interface ActionRegistrar {
	fun register(layer: ActionLayer, handlers: Map<ActionId, ActionHandler>, blocksBelow: Boolean = false): ActionRegistration
}
interface ActionDispatcher {
	val keymap: Keymap
	fun dispatch(chord: KeyChord): Boolean   // false: pass the key on
	fun invoke(id: ActionId): Boolean
	fun isLive(id: ActionId): Boolean
	fun live(): List<ActionDef>              // HammerActions.all order
}
class ActionRegistry(override val keymap: Keymap = Keymap.Default) : ActionRegistrar, ActionDispatcher

fun ActionRegistrar.registerWhileStarted(
	lifecycle: Lifecycle,
	layer: ActionLayer,
	handlers: Map<ActionId, ActionHandler>,
	blocksBelow: Boolean = false,
)
```

- [ ] **Step 1: Write the failing tests** (`ActionRegistryTest`, plain JUnit 5, no Koin):

```kotlin
private val registry = ActionRegistry()
private fun counting(counter: IntArray, index: Int = 0) = ActionHandler { counter[index]++ }

@Test fun `an unbound chord is not handled`() = assertFalse(registry.dispatch(KeyChord.mod(ChordKey.J)))
@Test fun `a bound chord with no handler is not handled`() = assertFalse(registry.dispatch(KeyChord.mod(ChordKey.N)))
@Test fun `a registered handler runs on its chord`() {
	val runs = IntArray(1)
	registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs)))
	assertTrue(registry.dispatch(KeyChord.mod(ChordKey.N))); assertEquals(1, runs[0])
}
@Test fun `the higher layer wins`()                       // Module and Item both register item.new; only Item runs
@Test fun `within a layer the most recent registration wins`()
@Test fun `unregistering restores the lower handler`()
@Test fun `a disabled handler falls through to the next registration`()
@Test fun `an overlay barrier hides lower handlers but not global ones`() {
	val runs = IntArray(2)
	registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
	registry.register(ActionLayer.Root, mapOf(HammerActions.ProjectSaveAll.id to counting(runs, 1)))
	registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)
	assertFalse(registry.dispatch(KeyChord.mod(ChordKey.N)))
	assertTrue(registry.dispatch(KeyChord.mod(ChordKey.S, shift = true)))
	assertEquals(listOf(0, 1), runs.toList())
}
@Test fun `a barrier's own handlers still run`()
@Test fun `local actions are never dispatched`()           // edit.save registered; dispatch(Mod+S) is false
@Test fun `live lists reachable actions in table order`()  // register nav.notes and nav.home; live() ids == [nav.home, nav.notes]
@Test fun `registerWhileStarted follows the lifecycle`() {
	val lifecycle = LifecycleRegistry()
	registry.registerWhileStarted(lifecycle, ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler {}))
	lifecycle.create(); assertFalse(registry.isLive(HammerActions.ItemNew.id))
	lifecycle.start(); assertTrue(registry.isLive(HammerActions.ItemNew.id))
	lifecycle.stop(); assertFalse(registry.isLive(HammerActions.ItemNew.id))
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.actions.ActionRegistryTest'`
- [ ] **Step 3: Implement.** Registrations carry `layer`, a monotonically increasing `seq`, `blocksBelow`, `handlers`. Resolution (the one non-obvious part):

```kotlin
private fun resolve(id: ActionId): ActionHandler? {
	val def = HammerActions.byId(id) ?: return null
	val ordered = registrations.sortedWith(
		compareByDescending<Registration> { it.layer.ordinal }.thenByDescending { it.seq }
	)
	var blocked = false
	for (reg in ordered) {
		if (!blocked || def.global) {
			reg.handlers[id]?.takeIf { it.isEnabled() }?.let { return it }
		}
		if (reg.blocksBelow) blocked = true
	}
	return null
}
```

`dispatch` maps the chord through `keymap.actionFor`, returns false for `ActionDispatch.Local`, then `invoke`. `isLive(id) = resolve(id) != null`. `unregister` is idempotent. `registerWhileStarted` uses Essenty `lifecycle.subscribe(onStart = ..., onStop = ...)`.
- [ ] **Step 4: Run, expect PASS.**

### Task 1.3: Compose key events to chords

**Files:**
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/KeyChordCompose.kt`
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/KeyChordComposeTest.kt`

**Interfaces:**
- Produces: `fun KeyEvent.toKeyChord(platform: ChordPlatform = currentChordPlatform): KeyChord?`, null unless `type == KeyEventType.KeyDown`.

- [ ] **Step 1: Write the failing tests.** Build events with a copy of `WindowShortcutsTest`'s `keyDown(key, ctrl, meta, alt, shift)` helper (`KeyEvent(key, type, isCtrlPressed, ...)`, `@OptIn(InternalComposeUiApi::class)`); for the layout case pass `nativeEvent` an AWT event subclass overriding `getExtendedKeyCode`, as composetexteditor's `LayoutKeyTest` does.

```kotlin
@Test fun `ctrl N is Mod+N off Apple`() =
	assertEquals(KeyChord.mod(ChordKey.N), keyDown(Key.N, ctrl = true).toKeyChord(ChordPlatform.Desktop))
@Test fun `cmd N is Mod+N on Apple`() =
	assertEquals(KeyChord.mod(ChordKey.N), keyDown(Key.N, meta = true).toKeyChord(ChordPlatform.DesktopMac))
@Test fun `on Apple, Ctrl chords are not actions`() {
	assertNull(keyDown(Key.N, ctrl = true).toKeyChord(ChordPlatform.DesktopMac))
	assertNull(keyDown(Key.K, ctrl = true).toKeyChord(ChordPlatform.DesktopMac))
}
@Test fun `off Apple, Meta chords are not actions`() = assertNull(keyDown(Key.N, meta = true).toKeyChord(ChordPlatform.Desktop))
@Test fun `AltGr chords are never actions`() {
	assertNull(keyDown(Key.Q, ctrl = true, alt = true).toKeyChord(ChordPlatform.Desktop))
	assertNull(keyDown(Key.S, ctrl = true, alt = true, shift = true).toKeyChord(ChordPlatform.Desktop))
	assertNull(keyDown(Key.N, meta = true, alt = true).toKeyChord(ChordPlatform.DesktopMac))
}
@Test fun `a key release is not a chord`()
@Test fun `a letter chord follows the active layout`()   // Linux host only (assumeTrue(hostOs == HostOs.Linux)): keyCode VK_X, extended VK_N, ctrl -> Mod+N
@Test fun `numpad digits alias the digit row`() {
	assertEquals(KeyChord.mod(ChordKey.Digit3), keyDown(Key.NumPad3, ctrl = true).toKeyChord(ChordPlatform.Desktop))
	assertEquals(KeyChord.mod(ChordKey.Digit1), keyDown(Key.One, ctrl = true).toKeyChord(ChordPlatform.Desktop))
}
@Test fun `the apostrophe key is not a digit`() = assertNull(keyDown(Key.Apostrophe, ctrl = true).toKeyChord(ChordPlatform.Desktop))
@Test fun `numpad enter is enter`() = assertEquals(
	KeyChord.mod(ChordKey.Enter, shift = true),
	keyDown(Key.NumPadEnter, ctrl = true, shift = true).toKeyChord(ChordPlatform.Desktop))
@Test fun `function keys need no modifier`() {
	assertEquals(KeyChord.bare(ChordKey.F6), keyDown(Key.F6).toKeyChord(ChordPlatform.Desktop))
	assertEquals(KeyChord.bare(ChordKey.F6, shift = true), keyDown(Key.F6, shift = true).toKeyChord(ChordPlatform.Desktop))
}
@Test fun `keys outside the table are not chords`() = assertNull(keyDown(Key.Tab, ctrl = true).toKeyChord(ChordPlatform.Desktop))
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*KeyChordComposeTest'`
- [ ] **Step 3: Implement.** Order: KeyDown only; Alt held → null; `primary = if (platform.modIsCmd) isMetaPressed else isCtrlPressed`; the other of Ctrl/Meta held → null; letters from `layoutKey` (`com.darkrockstudios.texteditor.input.layoutKey`); digits from `Key.Zero..Nine` and `Key.NumPad0..9`; `F1..F12`; `PageUp`/`PageDown` and `NumPadPageUp`/`NumPadPageDown`; `Enter`/`NumPadEnter`; `Equals`; `Minus`. Anything else → null. No symbol aliasing for AZERTY: X11 reports the `'` key on AZERTY and QWERTY alike, so aliasing would turn QWERTY Ctrl+' into Mod+4.
- [ ] **Step 4: Run, expect PASS.**

### Task 1.4: ProjectRootComponent owns the registry and the Root actions

**Files:**
- Modify: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/ProjectRoot.kt:27` (add `ComponentToaster` super-interface and `val actions: ActionRegistry`)
- Modify: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/ProjectRootComponent.kt:45-52` (add `ComponentToaster by ComponentToasterImpl()`), `:68-73` (drop `syncMenuAdded`), `:165-172` (drop `updateSyncMenu` call), `:461-526` (delete `onStart`/`onStop` menu code, `addMenuItems`, `updateSyncMenu`, `removeMenuItems`, `SYNC_MENU_ID`, `IMAGES_MENU_ID`; keep the `addMenu`/`removeMenu` constructor params, still threaded to the router until Task 3.1)
- Modify: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/projectroot/ProjectRootUi.kt:92-133` (add `Toaster(component, rootSnackbar)`; the `shortcutHost.bind` lambdas become `component.actions.invoke(HammerActions.ProjectSync.id)` and `component.actions.invoke(HammerActions.ProjectSaveAll.id)`; drop the inline save-all snackbar; provide locals, see below)
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/ActionLocals.kt`
- Test: `common/src/desktopTest/kotlin/components/projectroot/ProjectRootComponentTest.kt`

**Interfaces:**
- Consumes: `ActionRegistry`, `registerWhileStarted`, `HammerActions`.
- Produces:

```kotlin
// ProjectRoot
val actions: ActionRegistry

// ActionLocals.kt (package com.darkrockstudios.apps.hammer.common.compose.actions)
val LocalActionRegistrar: ProvidableCompositionLocal<ActionRegistrar?>
val LocalActionDispatcher: ProvidableCompositionLocal<ActionDispatcher?>
@Composable fun ProvideActions(registry: ActionRegistry, content: @Composable () -> Unit)
@Composable fun RegisterActions(layer: ActionLayer, handlers: Map<ActionId, ActionHandler>, blocksBelow: Boolean = false, enabled: Boolean = true)
```

`RegisterActions` registers in a `DisposableEffect(registrar, layer, blocksBelow, enabled)` and calls the latest handler lambdas through `rememberUpdatedState`, so recompositions do not re-register. `ProjectRootUi` wraps its content in `ProvideActions(component.actions)`.

Root registrations in `ProjectRootComponent` (`registerWhileStarted(lifecycle, ActionLayer.Root, ...)` from `init`): `nav.*` → `showDestination(...)`; `search.global` → `showGlobalSearch()`; `project.sync` → `startProjectSync()` with `isEnabled = { canSync }`; `project.saveAll` → `scope.launch { storeDirtyBuffers(); showToast(Res.string.save_all_toast) }`; `project.close` → the `onCloseProject` constructor callback.

- [ ] **Step 1: Write the failing tests** in `ProjectRootComponentTest` (reuse `newComponent()`, `context`, `mainTestDispatcher`, `projectClosed`):

```kotlin
@Test fun `Mod+3 shows the Notes destination`() = runTest(mainTestDispatcher) {
	val comp = newComponent(); context.resume(); advanceUntilIdle()
	val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3)); advanceUntilIdle()
	assertTrue(handled)
	assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
}
@Test fun `Mod+1 through Mod+5 reach every destination in nav rail order`()
@Test fun `Mod+Shift+F opens global search`()          // modalRouterState child is GlobalSearchModal
@Test fun `F3 starts a sync for a server synchronized project`() // isServerSynchronized true -> closeRequestHandlers == {Sync}
@Test fun `F3 is not handled for a local-only project`()          // dispatch(F3) is false
@Test fun `Mod+Shift+S saves every buffer and says so`()          // coVerify sceneEditor.storeAllBuffers(); first comp.toast == ToastMessage.Resource(save_all_toast)
@Test fun `Mod+Shift+W asks to close the project`()               // projectClosed
@Test fun `Root actions stop answering once the project is stopped`()
```

Delete the menu tests at `ProjectRootComponentTest.kt:692-776` (`Sync menu is added...`, `No sync menu for local-only projects`, `Turning project sync off and on removes and restores the sync menu`, `The images menu opens the gallery to browse`); rewrite `No sync menu, sync request or close sync when project sync is off` as `No sync action, sync request or close sync when project sync is off` asserting `!comp.actions.isLive(HammerActions.ProjectSync.id)`.
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.projectroot.ProjectRootComponentTest'`
- [ ] **Step 3: Implement** as described.
- [ ] **Step 4: Run, expect PASS.**

### Task 1.5: Desktop host

**Files:**
- Modify: `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/ProjectEditorWindow.kt:59` (`val backDispatcher = remember { BackDispatcher() }`), `:89` and `:135`, `:287` (stop creating and passing `ProjectShortcutHost`), `:96-124` (see below)
- Modify: `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/WindowShortcuts.kt` (delete `ProjectShortcut`, `toProjectShortcut`, `toProjectEditorShortcut`, `WindowShortcut.CloseProject`, `WindowShortcut.GlobalSearch`; rewrite `toProjectSelectionShortcut`)
- Modify: `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/ProjectSelectionWindow.kt:83-96` (no behavior change beyond the Mod rule)
- Test: `desktop/src/jvmTest/kotlin/com/darkrockstudios/apps/hammer/desktop/WindowShortcutsTest.kt`

**Interfaces:**
- Consumes: `toKeyChord`, `ProjectRoot.actions`, `HammerActions.AppQuit`.
- Produces: `internal fun KeyEvent.toProjectSelectionShortcut(platform: ChordPlatform = currentChordPlatform): WindowShortcut?` (`Back` on Esc KeyUp, `Quit` when `toKeyChord(platform) == KeyChord.mod(ChordKey.Q)`).

Editor window wiring:
- `onPreviewKeyEvent = { e -> e.toKeyChord()?.let(component.actions::dispatch) ?: false }`
- `onKeyEvent` keeps only Esc KeyUp → `backDispatcher.back()` (arming arrives in Task 8.1).
- Inside the window content: `DisposableEffect(component) { val reg = component.actions.register(ActionLayer.Root, mapOf(HammerActions.AppQuit.id to ActionHandler { onRequestClose(component, app, ApplicationState.CloseType.Application) })); onDispose { reg.unregister() } }`.

- [ ] **Step 1: Update `WindowShortcutsTest`.** Keep `ctrl Q quits from the project selection window` (pass `ChordPlatform.Desktop`), change `cmd Q ...` to pass `ChordPlatform.DesktopMac`, keep the AltGr test for the selection window, add `ctrl Q does not quit on macOS`, and delete the editor-window tests (`AltGr W does not close the project`, `ctrl W closes the project`, `ctrl shift F opens global search`, anything calling `toProjectEditorShortcut`/`toProjectShortcut`).
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :desktop:jvmTest --tests '*WindowShortcutsTest'`
- [ ] **Step 3: Implement** the window and `WindowShortcuts.kt` changes. The `BackDispatcher` remember fix has no automated test (window composable); verify by hand in Step 5.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Manual check (supervisor).** `./gradlew :desktop:run --args='--dev'`: Mod+1..5 switch modules from a focused editor, Mod+Shift+F opens search, Mod+Shift+S toasts, F3 syncs a server project, Mod+Shift+W prompts close, Mod+Q quits, Esc still backs out after cancelling a close prompt.

### Checkpoint 1 wrap-up

- [ ] Run `./gradlew :common:desktopTest --tests 'components.actions.*' --tests 'components.projectroot.*'`, `./gradlew :composeUi:desktopTest --tests '*KeyChordComposeTest'`, `./gradlew :desktop:jvmTest`, `./gradlew :architectureTests:test`. Expected: all green.
- [ ] Supervisor commits (message includes the two spike outcome lines).
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 2: Android and iOS hosts, delete the old host code

### Task 2.1: Android host

**Files:**
- Create: `android/src/main/kotlin/com/darkrockstudios/apps/hammer/android/AndroidChord.kt`
- Modify: `android/src/main/kotlin/com/darkrockstudios/apps/hammer/android/ProjectRootActivity.kt:73` (drop `shortcutHost`), `:188-208` (`dispatchKeyEvent`), `:227` (drop `shortcutHost` arg)
- Test: `android/src/test/kotlin/com/darkrockstudios/apps/hammer/android/AndroidChordTest.kt` (JUnit 5, as `WidgetConfigDataTest`)

**Interfaces:**
- Produces: `fun androidChord(keyCode: Int, metaState: Int): KeyChord?` (pure; uses only `android.view.KeyEvent` int constants, which compile to literals, so it runs in local unit tests).

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `ctrl N is Mod+N`() = assertEquals(KeyChord.mod(ChordKey.N), androidChord(KEYCODE_N, META_CTRL_ON or META_CTRL_LEFT_ON))
@Test fun `ctrl shift S is save all`() = assertEquals(KeyChord.mod(ChordKey.S, shift = true), androidChord(KEYCODE_S, META_CTRL_ON or META_SHIFT_ON))
@Test fun `bare F3 is the sync chord`() = assertEquals(KeyChord.bare(ChordKey.F3), androidChord(KEYCODE_F3, 0))
@Test fun `alt chords are not actions`() {
	assertNull(androidChord(KEYCODE_Q, META_CTRL_ON or META_ALT_ON or META_ALT_RIGHT_ON))
	assertNull(androidChord(KEYCODE_S, META_CTRL_ON or META_SHIFT_ON or META_ALT_ON))
}
@Test fun `meta chords are not actions`() = assertNull(androidChord(KEYCODE_N, META_META_ON))
@Test fun `lock keys do not block chords`() =
	assertEquals(KeyChord.mod(ChordKey.Digit3), androidChord(KEYCODE_NUMPAD_3, META_CTRL_ON or META_NUM_LOCK_ON or META_CAPS_LOCK_ON))
@Test fun `numpad enter is enter`() = assertEquals(KeyChord.mod(ChordKey.Enter, shift = true), androidChord(KEYCODE_NUMPAD_ENTER, META_CTRL_ON or META_SHIFT_ON))
@Test fun `keys outside the table are not chords`() = assertNull(androidChord(KEYCODE_TAB, META_CTRL_ON))
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :android:testDebugUnitTest --tests '*AndroidChordTest'`
- [ ] **Step 3: Implement.** Modifier tests use `META_CTRL_MASK`, `META_SHIFT_MASK`, `META_ALT_MASK`, `META_META_MASK` (lock bits ignored); primary = ctrl. Key map: `KEYCODE_A..Z`, `KEYCODE_0..9`, `KEYCODE_NUMPAD_0..9`, `KEYCODE_F1..F12`, `KEYCODE_PAGE_UP`/`PAGE_DOWN`, `KEYCODE_ENTER`/`NUMPAD_ENTER`, `KEYCODE_EQUALS`, `KEYCODE_MINUS`. `dispatchKeyEvent`: on `ACTION_DOWN`, `androidChord(event.keyCode, event.metaState)?.let { if (projectRoot?.actions?.dispatch(it) == true) return true }`, then `super`. The old Ctrl+Shift+F, F3 and Ctrl+Shift+S branches go.
- [ ] **Step 4: Run, expect PASS.**

### Task 2.2: iOS host

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/IosKeyCommands.kt`
- Create: `common/src/iosMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/IosActionBridge.kt`
- Modify: `common/src/iosMain/kotlin/com/darkrockstudios/apps/hammer/common/components/iosroot/IosRoot.kt:9-27` (add `fun currentActions(): ActionDispatcher?`) and `IosRootComponent.kt` (implement: the active `ProjectRootDestination`'s `component.actions`, else null)
- Modify: `composeUi/src/iosMain/kotlin/com/darkrockstudios/apps/hammer/common/MainViewController.kt:19-55` (drop `shortcutHost`: `fun MainViewController(root: IosRoot): UIViewController`)
- Modify: `ios/ios/ComposeContainer.swift:15-76`
- Test: `common/src/desktopTest/kotlin/components/actions/IosKeyCommandSpecsTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class IosKeyCommandSpec(val input: String, val command: Boolean, val shift: Boolean, val actionId: String)
fun iosKeyCommandSpecs(keymap: Keymap): List<IosKeyCommandSpec>   // Host actions only, every chord
// iosMain
fun ActionDispatcher.invokeRaw(id: String): Boolean
fun ActionDispatcher.isLiveRaw(id: String): Boolean
```

Inputs: letters lowercase (`"n"`), digits (`"1"`), F-keys the UIKit private-use characters `""` + (n - 1) (F3 = `""`, matching today's Swift), PageUp `"UIKeyInputPageUp"`, PageDown `"UIKeyInputPageDown"`, Enter `"\r"`, Equals `"="`, Minus `"-"`. `command = chord.primary`.

Swift (`ShortcutHostController` now takes `root: IosRoot` instead of the shortcut host): `keyCommands` maps `IosKeyCommandsKt.iosKeyCommandSpecs(keymap:)` over `root.currentActions()?.keymap` (empty when no project) to `UIKeyCommand(input:modifierFlags:action: #selector(performAction(_:)))` with `.command`/`.shift` flags and `propertyList = spec.actionId`; `performAction` calls `invokeRaw`; `canPerformAction(_:withSender:)` returns `isLiveRaw(id)` for `performAction`, so a non-live chord falls through to Compose.

- [ ] **Step 1: Write the failing tests:**

```kotlin
private val specs = iosKeyCommandSpecs(Keymap.Default)
@Test fun `every host chord becomes one key command`() = assertEquals(
	HammerActions.all.filter { it.dispatch == ActionDispatch.Host }.sumOf { it.defaultChords.size }, specs.size)
@Test fun `local actions get no key command`() = assertTrue(specs.none { it.actionId == "edit.save" })
@Test fun `Mod maps to command`() = assertEquals(IosKeyCommandSpec("1", command = true, shift = false, actionId = "nav.home"), specs.single { it.actionId == "nav.home" })
@Test fun `function keys use the UIKit private-use characters`() {
	assertEquals("", specs.single { it.actionId == "project.sync" }.input)
	assertEquals("", specs.single { it.actionId == "help.cheatSheet" }.input)
}
@Test fun `page keys use the UIKit input constants`() = assertEquals("UIKeyInputPageDown", specs.single { it.actionId == "item.next" }.input)
@Test fun `letters are lowercase`() = assertEquals("n", specs.single { it.actionId == "item.new" }.input)
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.actions.IosKeyCommandSpecsTest'`
- [ ] **Step 3: Implement** Kotlin and Swift as above.
- [ ] **Step 4: Run, expect PASS.** Then `./gradlew :composeUi:compileKotlinIosSimulatorArm64`; if the Linux host cannot compile Apple targets, say so in the commit body and leave the Swift unverified for Adam to build in Xcode.

### Task 2.3: Delete the old host code

**Files:**
- Delete: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/ProjectShortcutHost.kt`, `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/ProjectShortcutHostTest.kt`
- Modify: `composeUi/.../projectroot/ProjectRootUi.kt:97-114` (drop `shortcutHost` param and binding), `composeUi/.../projectroot/ProjectRootScaffold.kt:47-50, 86-99, 121-158` (drop `shortcutHost`)
- Delete: `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/menu.kt`; `ProjectEditorWindow.kt:223-258` (`EditorMenuBar`); `ApplicationState.kt:61-76` (`_menu`, `menu`, `addMenu`, `removeMenu`)
- Modify: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/MenuDescriptor.kt` (delete `KeyShortcut`; `MenuItemDescriptor.shortcut` becomes `val actionId: ActionId? = null`)
- Modify: `common/.../components/storyeditor/sceneeditor/SceneEditorComponent.kt:228-236` (save item: `actionId = HammerActions.EditSave.id` instead of `KeyShortcut(83, ctrl = true)`)

- [ ] **Step 1: Delete and rewire** as listed. `MatchesShortcutTest` stays (`matchesShortcut` still backs the Local focus modifiers).
- [ ] **Step 2: Compile everything.** `./gradlew :desktop:compileKotlinJvm :android:compileDebugKotlin :composeUi:compileKotlinDesktop`. Expected: success, and `grep -rn "ProjectShortcutHost\|KeyShortcut\b\|EditorMenuBar\|toDesktopShortcut" --include=*.kt --include=*.swift .` finds nothing outside `build/`.

### Checkpoint 2 wrap-up

- [ ] Run `./gradlew :common:desktopTest --tests 'components.*'`, `./gradlew :composeUi:desktopTest`, `./gradlew :desktop:jvmTest`, `./gradlew :android:testDebugUnitTest`. Expected: green.
- [ ] Manual (supervisor, via `/android-cli`): emulator with hardware keyboard, Ctrl+1..5, Ctrl+Shift+S, F3.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 3: Story context actions

### Task 3.1: Replace `addMenu`/`removeMenu` with `ActionRegistrar`

**Files (every constructor that changes):**
- `ProjectRootComponent.kt:45-52, 119-138`: drop `addMenu`, `removeMenu`; pass `actions` to the router.
- `ProjectRootRouter.kt:28-47`: `addMenu`/`removeMenu` → `private val actions: ActionRegistrar`; `:94-168` pass `actions = actions` to the four module components.
- `StoryEditorComponent.kt:23-59`: params → `actions: ActionRegistrar`; pass to `DetailsRouter` and `ListRouter`.
- `DetailsRouter.kt:22-33, 75-88`: → `actions`; pass to `SceneEditorComponent`.
- `ListRouter.kt:12-48`: add `actions`; pass to `SceneListComponent`.
- `SceneListComponent.kt:26-34`: add `private val actions: ActionRegistrar`.
- `SceneEditorComponent.kt:39-51`: → `actions`; `:337` and `:350` drop `addMenu(menu)`/`removeMenu(...)` and the `MenuDescriptor` build; keep setting and clearing `state.menuItems`; delete `getMenuId()` (`:584-586`).
- `NotesComponent.kt:12-19, 95-108` and `ViewNoteComponent.kt:30-39, 297-327`.
- `EncyclopediaComponent.kt:14-23, 103-115` and `ViewEntryComponent.kt:51-60, 517-575`.
- `TimeLineComponent.kt:40-50, 116-170` and `ViewTimeLineEventComponent.kt:49-61, 264-266, 456-503`.
- `TimeLineOverviewComponent.kt:51-59`: drop the params; delete `onStart`/`onStop` menu code `:127-138` and `MENU_ID`, `NEW_TIMELINE_ITEM_ID`.
- Callers: `ProjectEditorWindow.kt:68-77`, `ProjectRootActivity.kt:95-104`, `IosRootComponent.kt:43-51`.
- Delete `MenuDescriptor` from `data/MenuDescriptor.kt`; delete `android/src/main/kotlin/com/darkrockstudios/apps/hammer/android/TopAppBarDropdownMenu.kt` (unused, only consumer of `MenuDescriptor` there).
- Tests that pass `addMenu =`/`removeMenu =` or read `addedMenus`: `ProjectRootComponentTest`, `StoryEditorComponentTest`, `SceneEditorComponentTest`, `SceneListComponentTest` (add `actions`), `NotesComponentTest`, `ViewNoteComponentTest`, `EncyclopediaComponentTest`, `ViewEntryComponentTest`, `ViewEntryComponentExternalChangeTest`, `ViewEntryComponentImagesTest`, `ViewEntryTimelinesTest`, `TimeLineComponentTest`, `TimeLineOverviewComponentTest`, `ViewTimeLineEventComponentTest`. Each gets `private val registry = ActionRegistry()` and passes `actions = registry`.

**Interfaces:**
- Produces: every module and item component above takes `actions: ActionRegistrar` (named `actions` everywhere).

- [ ] **Step 1: Update the tests first.** Replace assertions on `addedMenus` with assertions on `state.value.menuItems`. `SceneEditorComponentTest.the editor menu is added on start and removed on stop` becomes `menu items appear on start and clear on stop` (`comp.onStart()` → non-empty; `comp.onStop()` → empty). `StoryEditorComponentTest.View drafts menu action pushes the drafts list and back returns to the editor` finds the item in `(detailsRouterState.value.active.instance as EditorDestination).component.state.value.menuItems`. `TimeLineOverviewComponentTest`: delete tests about the timeline menu.
- [ ] **Step 2: Run, expect compile failure.** `./gradlew :common:desktopTest --tests 'components.*'`
- [ ] **Step 3: Thread the parameter** through every file listed.
- [ ] **Step 4: Run, expect PASS**, and `grep -rn "addMenu\|removeMenu\|MenuDescriptor(" --include=*.kt .` finds nothing outside `build/`.

### Task 3.2: Scene list create dialogs in the component; `item.new` and `story.newGroup`

**Files:**
- Modify: `common/.../components/storyeditor/scenelist/SceneList.kt:10-45`
- Modify: `common/.../components/storyeditor/scenelist/SceneListComponent.kt:120-149` and `init`
- Modify: `composeUi/.../storyeditor/scenelist/SceneListUi.kt:155-156` (delete the two `rememberSaveable` create states), `:288-289`, `:333-350`, `:363-406` (read and write the component instead)
- Test: `common/src/desktopTest/kotlin/components/storyeditor/scenelist/SceneListComponentTest.kt`

**Interfaces:**
- Produces:

```kotlin
// SceneList
data class CreateRequest(val kind: Kind, val parent: SceneItem) { enum class Kind { Scene, Group } }
// State gains: val createRequest: CreateRequest? = null
fun beginCreate(kind: SceneList.CreateRequest.Kind, parent: SceneItem?)   // null parent = tree root
fun dismissCreate()
```

`createScene`/`createGroup` clear `createRequest` when done. Registrations (`registerWhileStarted(lifecycle, ActionLayer.Module, ...)`): `item.new` → `beginCreate(Scene, defaultCreateParent())`, `story.newGroup` → `beginCreate(Group, defaultCreateParent())`. `defaultCreateParent()`: the selected item if it is a Group; the selected scene's parent node if it is a Scene; else the tree root (`sceneSummary.sceneTree.root.value`). `SceneListUi` keeps its `lastCreateParent` latch for the closing animation.

- [ ] **Step 1: Write the failing tests** (reuse `newComponent()`, `selectedSceneFlow`, `sceneItem()`; summary from the test's existing scene-summary stub with a group `G` holding scene `S`):

```kotlin
@Test fun `Mod+N asks for a scene in the selected scene's group`() // emit S on selectedSceneFlow; dispatch Mod+N; createRequest == CreateRequest(Scene, G)
@Test fun `Mod+N with nothing selected asks at the root`()          // parent.isRootScene
@Test fun `Mod+Shift+N asks for a group`()                          // kind == Group
@Test fun `a selected group is the parent itself`()
@Test fun `creating clears the request`()                           // beginCreate then createScene(...) -> null
@Test fun `the list stops answering Mod+N once stopped`()           // context.stop(); dispatch is false
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.storyeditor.scenelist.*'`
- [ ] **Step 3: Implement** component and UI changes.
- [ ] **Step 4: Run, expect PASS**, plus `./gradlew :composeUi:desktopTest --tests '*SceneList*'` (existing `SceneListChaptersTest`, `SceneListFooterTest`, `SceneListHeaderTest`, `SceneListTipsTest` must stay green).

### Task 3.3: Scene editor item actions

**Files:**
- Modify: `common/.../components/storyeditor/sceneeditor/SceneEditorComponent.kt` (registrations in `init`; `actionId`s on menu items `:218-318`)
- Test: `common/src/desktopTest/kotlin/components/storyeditor/sceneeditor/SceneEditorComponentTest.kt`

Registrations (`ActionLayer.Item`, while started): `item.close` → `closeEditor()`; `story.focusMode` → `enterFocusMode()`; `story.readingMode` → `readScene()`; `story.toggleSceneInfo` → the existing private `toggleMetadata()`; `story.newDraft` → `beginSaveDraft()`; `story.viewDrafts` → `showDraftsList(sceneDef)`. Menu `actionId`s: close `item.close`, save `edit.save`, view drafts `story.viewDrafts`, save draft `story.newDraft`, metadata `story.toggleSceneInfo`, focus `story.focusMode`, read `story.readingMode`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `Mod+W closes the scene editor`()                 // close callback count 1
@Test fun `Mod+Shift+Enter enters focus mode for this scene`()
@Test fun `Mod+Shift+R reads this scene`()
@Test fun `Mod+Shift+D begins saving a draft`()             // state.isSavingDraft
@Test fun `Mod+Shift+H shows this scene's drafts`()
@Test fun `Mod+Shift+M toggles the scene info modal on a narrow layout`() // setLayoutMode(false); showMetadataModal flips
@Test fun `item actions are live only while started`()
@Test fun `menu items carry their action ids`()             // close item actionId == HammerActions.ItemClose.id
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.storyeditor.sceneeditor.SceneEditorComponentTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 3.4: Story module actions: next/prev scene and group, outline, reader

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/storyeditor/SceneOrder.kt`
- Modify: `common/.../components/storyeditor/StoryEditorComponent.kt` (registrations in `onCreate`)
- Test: `common/src/desktopTest/kotlin/components/storyeditor/SceneOrderTest.kt`, `StoryEditorComponentTest.kt`

**Interfaces:**
- Produces (internal, in `SceneOrder.kt`):

```kotlin
internal fun ImmutableTree<SceneItem>.sceneAfter(sceneId: Int): SceneItem?
internal fun ImmutableTree<SceneItem>.sceneBefore(sceneId: Int): SceneItem?
internal fun ImmutableTree<SceneItem>.firstSceneOfNextGroup(sceneId: Int): SceneItem?
internal fun ImmutableTree<SceneItem>.firstSceneOfPreviousGroup(sceneId: Int): SceneItem?
```

Scenes in order = indices `0 until totalNodes` (depth-first, as `visibleSceneNodes` walks) filtered to `Type.Scene`. A "group" is a run of consecutive scenes sharing a `parent` index:

```kotlin
// next group: first scene after the current whose parent differs from the current scene's parent
// previous group: walk back to the first scene whose parent differs (call its parent P),
// then keep walking back while the parent is still P; return the earliest scene of that run
```

Registrations (`ActionLayer.Module`, while started; `isEnabled` only while the details stack's active config is `DetailsRouter.Config.SceneEditor` for next/prev): `item.next`/`item.prev` → `showScene(...)` of `sceneAfter`/`sceneBefore` from `sceneEditor.getSceneSummaries().sceneTree`, disabled at the ends; `story.nextGroup`/`story.prevGroup` likewise; `story.toggleOutline` → `showOutlineOverview()` or `dismissDialog()` if already shown; `story.readingMode` → `showReader(null)` (the scene editor's Item-layer handler wins while a scene is open).

- [ ] **Step 1: Write the failing tests.** `SceneOrderTest` builds `Tree<SceneItem>().apply { setRoot(...) }.toImmutableTree()` with root → [S1, G1 → [S2, S3], G2 → [S4], S5]:

```kotlin
@Test fun `next and previous follow tree order across groups`() {
	assertEquals(2, tree.sceneAfter(1)?.id); assertEquals(4, tree.sceneAfter(3)?.id); assertNull(tree.sceneAfter(5))
	assertEquals(3, tree.sceneBefore(4)?.id); assertNull(tree.sceneBefore(1))
}
@Test fun `next group starts at its first scene`() {
	assertEquals(2, tree.firstSceneOfNextGroup(1)?.id); assertEquals(4, tree.firstSceneOfNextGroup(2)?.id)
	assertEquals(5, tree.firstSceneOfNextGroup(4)?.id); assertNull(tree.firstSceneOfNextGroup(5))
}
@Test fun `previous group starts at its first scene`() {
	assertEquals(2, tree.firstSceneOfPreviousGroup(4)?.id); assertEquals(1, tree.firstSceneOfPreviousGroup(3)?.id)
	assertNull(tree.firstSceneOfPreviousGroup(1))
}
```

`StoryEditorComponentTest` (stub `sceneEditor.getSceneSummaries()` with a two-group summary):

```kotlin
@Test fun `Mod+PageDown opens the next scene`()
@Test fun `Mod+PageUp at the first scene is not handled`()
@Test fun `Mod+Shift+PageDown opens the first scene of the next group`()
@Test fun `Mod+Shift+O toggles the outline overview`()      // dialogState child Outline, then None
@Test fun `Mod+Shift+R opens the reader when no scene is open`()
@Test fun `next and previous are not live without an open scene`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.storyeditor.*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Checkpoint 3 wrap-up

- [ ] Run `./gradlew :common:desktopTest`, `./gradlew :composeUi:desktopTest`, `./gradlew :desktop:jvmTest`, `./gradlew :architectureTests:test`. Expected: green.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 4: Notes, Encyclopedia, Timeline; menus show chords

### Task 4.1: Notes

**Files:**
- Modify: `common/.../components/notes/BrowseNotes.kt:8-24` and `BrowseNotesComponent.kt` (visible order)
- Modify: `common/.../components/notes/NotesComponent.kt` (Module registrations)
- Modify: `common/.../components/notes/ViewNoteComponent.kt` (Item registration)
- Modify: `composeUi/.../notes/BrowseNotesUi.kt:163-176` (`LaunchedEffect(visibleNotes) { component.onVisibleOrderChanged(visibleNotes.map { it.id }) }`)
- Test: `common/src/desktopTest/kotlin/components/notes/NotesComponentTest.kt`, `ViewNoteComponentTest.kt`

**Interfaces:**
- Produces: `BrowseNotes.onVisibleOrderChanged(noteIds: List<Int>)` and `BrowseNotes.visibleOrder: List<Int>` (defaults to `state.notes` order until the UI reports).

Registrations: `NotesComponent` (Module): `item.new` → `showCreateNote()`; `item.next`/`item.prev` → `navigation.replaceCurrent(ViewNoteConfig(id))` using the Browse destination's `visibleOrder`, enabled only while a `ViewNoteDestination` is active and a neighbor exists. `ViewNoteComponent` (Item): `item.close` → `confirmClose()`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// NotesComponentTest
@Test fun `Mod+N opens a new note`()                        // stack active is CreateNoteDestination
@Test fun `Mod+PageDown opens the next note in the order the list shows`() // onVisibleOrderChanged([3, 1, 2]); view 1; dispatch; active ViewNoteConfig(2)
@Test fun `next note is not live while browsing`()
// ViewNoteComponentTest (reuse startWith(note()), dismissViewCount)
@Test fun `Mod+W closes a clean note`()                     // dismissViewCount == 1
@Test fun `Mod+W on a dirty edit asks before closing`() = runTest(mainTestDispatcher) {
	val comp = startWith(note(content = "orig")); comp.beginEdit(); comp.onContentChanged("dirty")
	assertTrue(registry.dispatch(KeyChord.mod(ChordKey.W)))
	assertTrue(comp.state.value.confirmClose); assertEquals(0, dismissViewCount)
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.notes.*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 4.2: Encyclopedia

**Files:**
- Modify: `common/.../components/encyclopedia/EncyclopediaComponent.kt` (Module registrations)
- Modify: `composeUi/.../encyclopedia/ViewEntryUi.kt:309-321` (extract the close lambda to `val requestClose = { ... }`, used by `CrumbRow(onClose = requestClose)` and `RegisterActions(ActionLayer.Item, mapOf(HammerActions.ItemClose.id to ActionHandler { requestClose() }))`)
- Create: `composeUi/src/desktopTest/kotlin/ViewEntryTestFixtures.kt` (move `FakeViewEntry` out of `ViewEntryTimelinesUiTest.kt:45`)
- Test: `common/src/desktopTest/kotlin/components/encyclopedia/EncyclopediaComponentTest.kt`, `composeUi/src/desktopTest/kotlin/ViewEntryCloseChordTest.kt`

Registrations in `EncyclopediaComponent` (Module): `item.new` → `showCreateEntry()`; `item.next`/`item.prev` over the Browse destination's `getFilteredEntries()` order, enabled while a `ViewEntryDestination` is active; navigation replaces the current view config.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// EncyclopediaComponentTest
@Test fun `Mod+N opens a new entry`()
@Test fun `Mod+PageDown opens the next filtered entry`()
// ViewEntryCloseChordTest: compose ViewEntryUi with FakeViewEntry inside ProvideActions(registry)
@Test fun `Mod+W on a dirty entry edit asks before closing`()  // start text edit, type; dispatch Mod+W; fake.confirmCloseCalls == 1; closeEntry not called
@Test fun `Mod+W on a clean entry closes it`()                 // closeEntry called once
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.encyclopedia.*'` and `./gradlew :composeUi:desktopTest --tests '*ViewEntry*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 4.3: Timeline

**Files:**
- Modify: `common/.../components/timeline/TimeLineComponent.kt` (Module: `item.new` → `showCreateEvent()`)
- Modify: `common/.../components/timeline/ViewTimeLineEventComponent.kt` (Item: `item.close` → `confirmClose()`; covers both the stack view and the event dialog)
- Test: `TimeLineComponentTest.kt`, `ViewTimeLineEventComponentTest.kt`

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `Mod+N opens a new event on the selected timeline`()      // active CreateEventConfig(timelineId = selected)
@Test fun `Mod+W on a clean event closes it`()
@Test fun `Mod+W on a dirty event edit asks before closing`()       // state.confirmClose true; close callback not called
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.timeline.*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 4.4: Dropdown menus show chords

**Files:**
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/ChordLabel.kt`
- Modify: `composeUi/.../compose/TopAppBarDropdownMenu.kt:44-52` (`DropdownMenuItem(trailingIcon = { item.actionId?.let { ChordLabel(it) } })`)
- Modify: `composeUi/.../storyeditor/scenelist/SceneListUi.kt:533-592` (`OverflowMenu`: trailing `ChordLabel` on the outline and reader rows)
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/MenuChordTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable fun rememberShowChords(): Boolean  // currentChordPlatform.showsChordsByDefault || LocalInputModeManager.current.inputMode == InputMode.Keyboard
@Composable fun ChordLabel(id: ActionId)       // HdMonoLabel(primary chord label) when shown and bound; nothing otherwise
```

Keymap source: `LocalActionDispatcher.current?.keymap ?: Keymap.Default`. `DetailViewDropdownMenu` inherits through `TopAppBarDropdownMenu`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `menu rows show their chord`()        // item with actionId ItemClose; open menu; onNodeWithText(KeyChord.mod(ChordKey.W).label().uppercase()) exists
@Test fun `rows without an action show none`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*MenuChordTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 4.5 (optional, cut first if short on time): Timeline next/previous event

**Files:** `TimeLineComponent.kt`, `TimeLineComponentTest.kt`

`item.next`/`item.prev` (Module) while `ViewEventDestination` is active: neighbor in the selected timeline's event order (the order `TimeLineOverview` draws), navigation replaces the current `ViewEventConfig`.

- [ ] **Step 1: Failing test** `Mod+PageDown opens the next event on this timeline`.
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.timeline.TimeLineComponentTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Checkpoint 4 wrap-up

- [ ] Run `./gradlew :common:desktopTest`, `./gradlew :composeUi:desktopTest`. Expected: green.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 5: Overlay barriers

### Task 5.1: Modal components block lower layers

**Files:**
- Modify: `common/.../components/projectroot/ProjectRootComponent.kt` (`onCreate`: subscribe to `modalRouter.state`; while the child is not `ModalDestination.None`, hold `actions.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)`; release otherwise)
- Modify: `common/.../components/storyeditor/StoryEditorComponent.kt` (same over `dialogRouter`)
- Test: `ProjectRootComponentTest.kt`, `StoryEditorComponentTest.kt`

- [ ] **Step 1: Write the failing tests:**

```kotlin
// ProjectRootComponentTest
@Test fun `Mod+N with focus mode open does nothing`() = runTest(mainTestDispatcher) {
	val comp = newComponent(); context.resume(); advanceUntilIdle()
	comp.showDestination(ProjectRoot.DestinationTypes.Notes); comp.showFocusMode(sceneItem()); advanceUntilIdle()
	assertFalse(comp.actions.dispatch(KeyChord.mod(ChordKey.N)))
	val notes = assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
	assertIs<Notes.Destination.BrowseNotesDestination>(notes.component.stack.value.active.instance)
}
@Test fun `global actions still run over focus mode`()        // Mod+Shift+S handled; storeAllBuffers verified
@Test fun `closing the modal restores module actions`()
// StoryEditorComponentTest
@Test fun `Mod+N is blocked while the outline overview is open`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.projectroot.*' --tests 'components.storyeditor.StoryEditorComponentTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 5.2: Dialog shells block lower layers

**Files:**
- Modify: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/AnimatedFullScreenDialog.kt:80-121` (`AnimatedDialogContainer` gains `blocksActions: Boolean = true`; inside the `Dialog` content `RegisterActions(ActionLayer.Overlay, emptyMap(), blocksBelow = true, enabled = blocksActions && isOpen)`; if the Task 1.0 spike said **no**, also add the `onPreviewKeyEvent` dispatch described there)
- Modify: same file `AnimatedDialog` (`:146-180`) and `designsystem/HdSearchableListDialog.kt:60-104`: thread `blocksActions`
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/DialogBarrierTest.kt`

- [ ] **Step 1: Write the failing tests** (compose inside `ProvideActions(registry)`, with a Module `item.new` handler registered on `registry`):

```kotlin
@Test fun `an open dialog blocks module chords`()          // AnimatedDialog(visible = true); dispatch(Mod+N) false
@Test fun `closing the dialog restores them`()             // visible = false, wait for idle; dispatch true
@Test fun `a dialog with blocksActions false does not block`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*DialogBarrierTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus `./gradlew :composeUi:desktopTest --tests '*AnimatedDialogChromeTest'`.

### Checkpoint 5 wrap-up

- [ ] Run `./gradlew :common:desktopTest`, `./gradlew :composeUi:desktopTest`. Expected: green.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 6: Command palette and cheat sheet

### Task 6.1: Fuzzy rank

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/actions/RankActions.kt`
- Test: `common/src/desktopTest/kotlin/components/actions/FuzzyRankTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class PaletteEntry(val id: ActionId, val label: String, val category: String, val chord: KeyChord?)
fun rankActions(query: String, entries: List<PaletteEntry>): List<PaletteEntry>
```

Scoring on lowercased, trimmed `query` against lowercased `label` (blank query returns `entries` unchanged):

```kotlin
// 400 label starts with query
// 300 some word of label starts with query
// 200 label contains query
// 100 - gaps: query is a subsequence of label; gaps = skipped chars between first and last match; floor 1
// no match: dropped
// sort by score descending, stable (ties keep input order)
```

- [ ] **Step 1: Write the failing tests:**

```kotlin
private fun e(label: String) = PaletteEntry(ActionId(label), label, "cat", null)
@Test fun `a blank query keeps table order`()
@Test fun `prefix beats word start beats substring beats subsequence`() = assertEquals(
	listOf("Notes go", "Go to Notes", "Footnotes", "New outline item"),
	rankActions("no", listOf(e("New outline item"), e("Footnotes"), e("Go to Notes"), e("Notes go"))).map { it.label })
@Test fun `non matches are dropped`() = assertEquals(emptyList(), rankActions("xyz", listOf(e("Go to Notes"))))
@Test fun `case is ignored`()
@Test fun `ties keep input order`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.actions.FuzzyRankTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 6.2: Palette and cheat sheet components, `commandOverlay` slot

**Files:**
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/commandpalette/CommandPalette.kt`, `CommandPaletteComponent.kt`, `CheatSheet.kt`, `CheatSheetComponent.kt`
- Create: `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/CommandOverlayRouter.kt`
- Modify: `ProjectRoot.kt` (slot, sealed `CommandOverlay`, show/dismiss), `ProjectRootComponent.kt` (router; Root registrations `palette.open` → `showCommandPalette()`, `help.cheatSheet` → toggles `showCheatSheet()`/`dismissCommandOverlay()`)
- Test: `common/src/desktopTest/kotlin/components/commandpalette/CommandPaletteComponentTest.kt`, `CheatSheetComponentTest.kt`, `ProjectRootComponentTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface CommandPalette {
	val state: Value<State>
	fun run(id: ActionId)
	fun dismiss()
	data class State(val entries: List<PaletteEntry> = emptyList())
}
class CommandPaletteComponent(componentContext: ComponentContext, actions: ActionRegistry, onDismiss: () -> Unit) : ComponentBase(componentContext), CommandPalette

interface CheatSheet {
	val state: Value<State>
	fun dismiss()
	data class Row(val id: ActionId, val label: String, val chords: List<KeyChord>, val live: Boolean)
	data class Section(val category: String, val rows: List<Row>)
	data class State(val sections: List<Section> = emptyList())
}
class CheatSheetComponent(componentContext: ComponentContext, actions: ActionDispatcher, onDismiss: () -> Unit) : ComponentBase(componentContext), CheatSheet

class CommandOverlayRouter(componentContext: ComponentContext, actions: ActionRegistry) {
	val state: Value<ChildSlot<Config, ProjectRoot.CommandOverlay>>
	fun showPalette(); fun showCheatSheet(); fun dismiss()
	@Serializable sealed class Config { @Serializable data object None : Config(); @Serializable data object Palette : Config(); @Serializable data object CheatSheet : Config() }
}
// ProjectRoot
sealed class CommandOverlay { data object None : CommandOverlay(); data class PaletteOverlay(val component: CommandPalette) : CommandOverlay(); data class CheatSheetOverlay(val component: CheatSheet) : CommandOverlay() }
val commandOverlayState: Value<ChildSlot<CommandOverlayRouter.Config, CommandOverlay>>
fun showCommandPalette(); fun showCheatSheet(); fun dismissCommandOverlay()
```

`CommandPaletteComponent`: in `init`, snapshot `actions.live()` minus `palette.open`, resolve labels and category labels through `StrRes` (Koin `inject()`), then register its own `ActionLayer.Overlay` barrier. `run(id)`: unregister the barrier, call `onDismiss()`, then `actions.invoke(id)`. `dismiss()` and `onDestroy` also unregister. `CheatSheetComponent` snapshots every `HammerActions.all` entry into sections in `ActionCategory` order with `live = actions.isLive(id)`; it registers no barrier (its dialog shell does).

- [ ] **Step 1: Write the failing tests** (`ComponentTest` base; `setupComponentKoin(module { single<StrRes> { KeyStrRes() } })`; labels read as `<action_nav_notes>`):

```kotlin
// CommandPaletteComponentTest
@Test fun `the palette lists live actions only`()        // registry has nav.notes and item.new live; entries ids == [nav.notes, item.new]
@Test fun `the palette does not list itself`()
@Test fun `running an action closes the palette then runs it`() // dismissed == true; module handler ran once
@Test fun `while open, chords below the palette are blocked`()  // dispatch(Mod+N) false before run
// CheatSheetComponentTest
@Test fun `sections follow categories in table order`()
@Test fun `dead actions are marked`()                     // only nav.home registered; row nav.notes live == false
// ProjectRootComponentTest
@Test fun `Mod+K opens the palette and F1 the cheat sheet`()
@Test fun `Mod+K over focus mode opens the palette and keeps focus mode`() {
	// showFocusMode(scene); dispatch Mod+K
	// commandOverlayState child is PaletteOverlay; modalRouterState child is still FocusModeModal
	// palette entries contain no item.new (blocked by the focus mode barrier) but do contain project.saveAll
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.commandpalette.*' --tests 'components.projectroot.*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus `./gradlew :architectureTests:test` (components live under `components.commandpalette`).

### Task 6.3: Palette and cheat sheet UI

**Files:**
- Modify: `composeUi/.../compose/designsystem/HdSearchableListDialog.kt:60-200` (new params, below)
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/commandpalette/CommandPaletteUi.kt`, `CheatSheetUi.kt`
- Modify: `common/src/commonMain/composeResources/values/strings_actions.xml` (add): `palette_title` "Commands", `palette_search_placeholder` "Type a command", `palette_empty` "No matching commands", `palette_close` "Close", `cheat_sheet_title` "Keyboard shortcuts", `cheat_sheet_close` "Close"
- Modify: `composeUi/.../projectroot/ProjectRootUi.kt` (`CommandOverlayContent(component)` after `ModalContent`, outside the modal slot, so it composes over focus mode)
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/commandpalette/CommandPaletteUiTest.kt`

**Interfaces:**
- Produces: `HdSearchableListDialog(..., filter: ((query: String, items: List<T>) -> List<T>)? = null, keyboardSelection: Boolean = false, autoFocusSearch: Boolean = false, blocksActions: Boolean = true)`. With `keyboardSelection`, a highlighted index (reset to 0 on query change) moves on Up/Down via `onPreviewKeyEvent` on the `HdSearchField`, Enter selects it, the row draws the selected style, and the list scrolls it into view. `autoFocusSearch` uses the `GlobalSearchUi.kt:102-103` pattern (`FocusRequester` + `LaunchedEffect(Unit) { requestFocus() }`).
- `@Composable fun CommandPaletteUi(component: CommandPalette)`: `HdSearchableListDialog(visible = true, items = state.entries, itemLabel = { it.label }, itemTrailing = { it.chord?.label() ?: "" }, filter = ::rankActions, keyboardSelection = true, autoFocusSearch = true, blocksActions = false, onSelect = { component.run(it.id) }, onDismiss = component::dismiss)`. `blocksActions = false` because the component holds its own barrier and must drop it before `run` invokes.
- `@Composable fun CheatSheetUi(component: CheatSheet)`: `AnimatedDialog` + `HdHairlineDialogShell(title = cheat_sheet_title)`; per section `HdSectionHeader(marker = "%02d".format(index + 1), title = section.category)`; rows with the label and the chord labels as `HdMonoLabel`, `alpha(0.5f)` when `!live`.

- [ ] **Step 1: Write the failing tests** (`FakeCommandPalette` records `run` ids; entries "Go to Notes", "New item", "Save all"):

```kotlin
@Test fun `the search field has focus on open`()          // performTextInput on the root without clicking narrows to "Save all"
@Test fun `Down then Enter runs the second result`()      // pressKey(DirectionDown); pressKey(Enter); fake.ran == [entries[1].id]
@Test fun `typing ranks with the fuzzy filter`()          // "no" puts "Go to Notes" first
@Test fun `Esc dismisses`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*CommandPaletteUiTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus existing `./gradlew :composeUi:desktopTest --tests '*SearchFilterTest'` and any `HdSearchableListDialog` users still green.

### Checkpoint 6 wrap-up

- [ ] Run `./gradlew :common:desktopTest`, `./gradlew :composeUi:desktopTest`, `./gradlew :architectureTests:test`. Expected: green.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 7: Pane focus, F6, tree and list keyboard navigation

### Task 7.1: Pane focus and F6

**Files:**
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/focus/PaneFocus.kt`
- Modify: `composeUi/.../projectroot/ProjectRootUi.kt` (wrap content in `ProvidePaneFocus`; focus the first pane on destination change in keyboard mode)
- Modify: `composeUi/.../storyeditor/StoryEditorUi.kt:62-84` (list modifier `.focusPane(PaneId.List)`, details modifier `.focusPane(PaneId.Detail)`)
- Modify: `composeUi/.../notes/NotesUi.kt`, `encyclopedia/EncyclopediaUi.kt`, `timeline/TimeLineUi.kt` (browse screens `PaneId.List`, view screens `PaneId.Detail`)
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/focus/PaneCycleTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class PaneId { List, Detail }   // ordinal is F6 order
@Stable class PaneFocusState {
	val registeredPanes: List<PaneId>            // snapshot state, ordinal order
	val focusedPane: PaneId?
	fun isContainerFocused(id: PaneId): Boolean  // the pane container itself, not a child
	fun focus(id: PaneId, reveal: Boolean = false): Boolean
	fun cycle(forward: Boolean): Boolean
	fun focusFirstIfNothingFocused(): Boolean
	fun revealRequests(id: PaneId): Int          // bumped by focus(id, reveal = true)
}
val LocalPaneFocus: ProvidableCompositionLocal<PaneFocusState?>
@Composable fun ProvidePaneFocus(content: @Composable () -> Unit)   // registers pane.next / pane.prev (Root) via RegisterActions
fun Modifier.focusPane(id: PaneId): Modifier  // focusRequester + onFocusChanged(container) + focusRestorer + focusGroup + focusable
```

`ProjectRootUi`: `LaunchedEffect(routerState.active.configuration, paneFocus.registeredPanes) { if (LocalInputModeManager.current.inputMode == InputMode.Keyboard) paneFocus.focusFirstIfNothingFocused() }` (read the input mode outside the effect).

- [ ] **Step 1: Write the failing tests** (compose `ProvideActions(registry) { ProvidePaneFocus { Row { Box(focusPane(List)) { focusable child "a" }; Box(focusPane(Detail)) { focusable child "b" } } } }`; send F6 by `registry.dispatch(KeyChord.bare(ChordKey.F6))` on the UI thread):

```kotlin
@Test fun `F6 moves focus to the next pane and wraps`()
@Test fun `Shift+F6 goes back`()
@Test fun `a pane gets back the child it last had focused`()
@Test fun `F6 is not live with no panes`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*PaneCycleTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 7.2: Scene tree cursor

**Files:**
- Modify: `composeUi/.../storyeditor/scenelist/scenetree/SceneTreeState.kt:44-62` (save `cursorId` with `rememberSaveable`), `:64-296` (cursor API)
- Modify: `composeUi/.../storyeditor/scenelist/scenetree/SceneTree.kt:43-90` (key handling on the `LazyColumn`; pass cursor to rows)
- Modify: `composeUi/.../storyeditor/scenelist/scenetree/SceneTreeNode.kt` (`cursor: Boolean` param; when true and the tree has focus, `Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outline)`)
- Modify: `composeUi/.../storyeditor/scenelist/SceneListUi.kt` (`onOpen` → `component.onSceneSelected`; `LaunchedEffect(paneFocus.revealRequests(PaneId.List)) { state.selectedSceneItem?.let { treeState.revealCursor(it.id) } }`)
- Test: `composeUi/src/desktopTest/kotlin/SceneTreeKeyboardTest.kt` (package `...scenelist.scenetree`; trees from `SceneTreeTestFixtures.kt` `sceneItem`/`sceneNode`; setup as `SceneTreeDragTest.showTree`)

**Interfaces:**
- Produces on `SceneTreeState`:

```kotlin
var cursorId: Int                     // NO_SELECTION when unset
fun moveCursor(delta: Int)            // over visibleNodes, clamped
fun cursorToStart(); fun cursorToEnd()
fun cursorRight()                     // collapsed group: expand; expanded group: first child; scene: nothing
fun cursorLeft()                      // expanded group: collapse; else: parent (never the root)
fun revealCursor(id: Int)             // un-collapse ancestors, set cursor, scroll into view
```

Keys on the tree (KeyDown, no modifiers): Up/Down, Home/End, Left/Right as above, Enter → `onOpen(scene)` for a scene, `toggleExpanded` for a group. The first Down with no cursor puts it on the first row.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `Down and Up move the cursor over visible rows`()
@Test fun `Right expands a collapsed group then enters it`()
@Test fun `Left collapses then climbs to the parent`()
@Test fun `Home and End jump to the ends`()
@Test fun `Enter on a scene opens it`()
@Test fun `Enter on a group toggles it`()
@Test fun `reveal expands the ancestors and puts the cursor on the scene`()
@Test fun `the cursor row scrolls into view`()             // short viewport; End; listState shows the last row
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*SceneTreeKeyboardTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus `--tests '*SceneTreeDragTest' --tests '*SceneTreeVisibilityTest' --tests '*SceneTreeInsertPositionTest'`.

### Task 7.3: Shared list navigation for notes and entries

**Files:**
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/focus/KeyboardListNavigation.kt`
- Modify: `composeUi/.../notes/BrowseNotesUi.kt:323-345` (staggered grid), `composeUi/.../encyclopedia/BrowseEntriesUi.kt:226-240` (grid)
- Test: `composeUi/src/desktopTest/kotlin/BrowseNotesKeyboardTest.kt`, `composeUi/src/desktopTest/kotlin/BrowseEntriesKeyboardTest.kt`

**Interfaces:**
- Produces:

```kotlin
internal fun listCursorStep(current: Int, count: Int, key: Key, horizontalSteps: Boolean): Int?  // null: not a navigation key
fun Modifier.keyboardListNavigation(
	itemCount: Int,
	cursor: Int,
	onCursorChange: (Int) -> Unit,
	onActivate: (Int) -> Unit,
	scrollToItem: suspend (Int) -> Unit,
	horizontalSteps: Boolean = false,   // grids: Left/Right step too
): Modifier
```

The grid container is the single focus stop; cards are not focusable. Cursor index is `rememberSaveable`, clamped when the visible list shrinks; the card under the cursor draws the hairline `outline` border while the grid has focus. Enter calls `component.viewNote(id)` / the existing entry open callback.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// BrowseNotesKeyboardTest
@Test fun `Down moves the cursor across the visible notes`()
@Test fun `Enter opens the note under the cursor`()       // fake BrowseNotes records viewNote(id)
@Test fun `the cursor stays on a visible note after filtering`()
@Test fun `listCursorStep clamps at both ends`()          // pure: (0, 3, DirectionUp) -> 0; (2, 3, MoveEnd) -> 2; (0, 3, Tab) -> null
// BrowseEntriesKeyboardTest
@Test fun `Enter opens the entry under the cursor`()
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*BrowseNotesKeyboardTest' --tests '*BrowseEntriesKeyboardTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Task 7.4 (optional, cut first if short on time): Keyboard scene reorder

**Files:** `SceneTreeState.kt`, `SceneTreeUtils.kt`, `SceneTreeKeyboardTest.kt`

Focus-local on the tree (not a table action): Mod+Shift+Up/Down (Cmd on Apple, via `currentChordPlatform.modIsCmd`) moves the cursor row one sibling up or down through `moveItem(MoveRequest)`.

- [ ] **Step 1: Failing tests** for `internal fun keyboardMoveRequest(tree: ImmutableTree<SceneItem>, id: Int, up: Boolean): MoveRequest?`: before the previous sibling (`InsertPosition(coords of previous sibling, before = true)`), after the next sibling (`before = false`), null at either end of the parent; and `Mod+Shift+Down moves the cursor scene after its next sibling` through the UI.
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*SceneTreeKeyboardTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**

### Checkpoint 7 wrap-up

- [ ] Run `./gradlew :composeUi:desktopTest`, `./gradlew :common:desktopTest`. Expected: green.
- [ ] Manual (supervisor): F6 between tree and editor; arrows and Enter in the tree; arrows and Enter in Notes and Encyclopedia.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.

---

## Checkpoint 8: Esc ladder, tooltips, docs, changelog

### Task 8.1: Esc ladder

**Files:**
- Create: `composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/focus/EscapeToPane.kt`
- Modify: `composeUi/.../storyeditor/StoryEditorUi.kt:153-196` (details pane `.escapeToPane { ... }`)
- Modify: notes, encyclopedia and timeline view screens (`ViewNoteUi`, `ViewEntryUi`, `ViewTimeLineEventUi` roots) `.escapeToPane { ... }`
- Modify: `desktop/.../ProjectEditorWindow.kt` `onKeyEvent` and `desktop/.../ProjectSelectionWindow.kt:83-96` (`val escArming = remember { EscapeArming() }`; back only when `escArming.onKeyEvent(event)` is true)
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/focus/EscLadderTest.kt`

**Interfaces:**
- Produces:

```kotlin
class EscapeArming {
	/** True only for an Esc release whose press this handler also saw. */
	fun onKeyEvent(event: KeyEvent): Boolean
}
fun Modifier.escapeToPane(onEscape: () -> Boolean): Modifier   // onKeyEvent; consumes the armed release when onEscape returns true
```

Arming body:

```kotlin
fun onKeyEvent(event: KeyEvent): Boolean {
	if (event.key != Key.Escape) return false
	return when (event.type) {
		KeyEventType.KeyDown -> { armed = true; false }
		KeyEventType.KeyUp -> armed.also { armed = false }
		else -> false
	}
}
```

Both handlers return false on the press so the window arms too. `onEscape` policies:
- Story details pane, multi-pane, a scene editor open: `paneFocus.focus(PaneId.List, reveal = true)`; true. The second Esc bubbles to the window: `StoryEditorComponent.onBack` closes the scene.
- Story details pane single-pane, and module view screens: if `!paneFocus.isContainerFocused(PaneId.Detail)`, focus the container (edit mode kept); true. Otherwise false, so the window backs out; the list then regains focus through `focusFirstIfNothingFocused` and its saved cursor.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `an Esc release without its press does nothing`() {
	val arming = EscapeArming()
	assertFalse(arming.onKeyEvent(keyEvent(Key.Escape, KeyEventType.KeyUp)))
	assertFalse(arming.onKeyEvent(keyEvent(Key.Escape, KeyEventType.KeyDown)))
	assertTrue(arming.onKeyEvent(keyEvent(Key.Escape, KeyEventType.KeyUp)))
	assertFalse(arming.onKeyEvent(keyEvent(Key.Escape, KeyEventType.KeyUp)))
}
@Test fun `Esc in the detail pane moves focus to the list and requests a reveal`() // focusPane(List)+focusPane(Detail)+escapeToPane; text field focused; Esc; list focused; revealRequests(List) == 1
@Test fun `a second Esc goes back`()                       // outer onKeyEvent with its own arming counts one back()
@Test fun `single pane: the first Esc leaves the text field and keeps editing`()
@Test fun `a dialog dismissed by Esc does not also step back`() // AnimatedDialog with focus consumes the press; release reaches the outer handler; back count 0
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*EscLadderTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus `./gradlew :desktop:jvmTest` (`WindowShortcutsTest` Back cases).

### Task 8.2: Tooltips from the keymap

**Files:**
- Modify: `composeUi/.../compose/actions/ChordLabel.kt` (add `fun chordTooltip(label: String, id: ActionId, keymap: Keymap = Keymap.Default, platform: ChordPlatform = currentChordPlatform): String`)
- Modify: `composeUi/.../compose/markdowneditor/MarkdownFormatBar.kt:270, 278, 288, 368` (use `chordTooltip` with `EditBold`, `EditItalic`, `EditStrikethrough`, `EditInsertImage`)
- Delete: `composeUi/src/{commonMain,desktopMain,androidMain,iosMain}/kotlin/com/darkrockstudios/apps/hammer/common/compose/markdowneditor/ShortcutHint.kt`
- Test: `composeUi/src/desktopTest/kotlin/com/darkrockstudios/apps/hammer/common/compose/actions/ChordTooltipTest.kt`

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun `tooltips name the chord per platform`() {
	assertEquals("Bold (Ctrl+B)", chordTooltip("Bold", HammerActions.EditBold.id, platform = ChordPlatform.Desktop))
	assertEquals("Insert image (⌘⇧I)", chordTooltip("Insert image", HammerActions.EditInsertImage.id, platform = ChordPlatform.DesktopMac))
}
@Test fun `an action without a chord is just its label`() // keymap with no chords for edit.bold -> "Bold"
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :composeUi:desktopTest --tests '*ChordTooltipTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS**, plus `--tests '*FormatBar*' --tests '*MarkdownFormatBarImageTest'`.

### Task 8.3: Docs and changelog

**Files:**
- Modify: `docs/KEYBOARD-SHORTCUTS.md` (rewrite)
- Modify: `CHANGELOG.md` (`## [4.0.0] - Unreleased`, under `New features` and `Improvements`)
- Modify: `common/build.gradle.kts` (`tasks.named<Test>("desktopTest") { val doc = rootProject.file("docs/KEYBOARD-SHORTCUTS.md"); inputs.file(doc); systemProperty("hammer.keyboardShortcutsDoc", doc.absolutePath) }`)
- Test: `common/src/desktopTest/kotlin/components/actions/KeyboardShortcutsDocTest.kt`

Doc content: the Mod rule (Cmd on macOS/iOS, Ctrl elsewhere; the other key held means no chord) replacing the old "Ctrl and Cmd interchangeable" section; exact modifiers and no Alt (issue #859); one table per `ActionCategory` listing each action id in backticks, its chords and what it does; the Local editing chords including text size (Mod+= / Mod+Shift+=, Mod+-, Mod+0, NumPad Plus/Minus/0 aliases from `ShortcutModifiers.kt:57-70`); F6 pane cycling; tree and list keys; the Esc ladder; hosts (desktop window `onPreviewKeyEvent`, Android `dispatchKeyEvent`, iOS generated `UIKeyCommand`s); how to add an action (table entry, string, handler registration, doc row).

CHANGELOG lines (style of the existing entries, no em dashes):
- New features: `Keyboard: drive a project without the mouse. Ctrl/Cmd+1 to 5 jump between Home, Story, Notes, Encyclopedia and Timeline, F6 moves between panes, the arrow keys and Enter work in the scene tree and lists, Ctrl/Cmd+K opens a command palette and F1 lists every shortcut`
- Improvements: `Shortcuts: Ctrl/Cmd+W now closes the open scene, note, entry or event; Ctrl/Cmd+Shift+W closes the project. On macOS, project shortcuts use Cmd only, leaving Control for text editing`

- [ ] **Step 1: Write the failing test:**

```kotlin
@Test fun `every action id is documented`() {
	val doc = File(System.getProperty("hammer.keyboardShortcutsDoc")).readText()
	assertEquals(emptyList(), HammerActions.all.map { it.id.value }.filter { "`$it`" !in doc })
}
```

- [ ] **Step 2: Run, expect FAIL.** `./gradlew :common:desktopTest --tests 'components.actions.KeyboardShortcutsDocTest'`
- [ ] **Step 3: Rewrite the doc and add the changelog lines.**
- [ ] **Step 4: Run, expect PASS.**

### Checkpoint 8 wrap-up

- [ ] Run `./gradlew desktopTest`, `./gradlew :desktop:jvmTest`, `./gradlew :android:testDebugUnitTest`, `./gradlew :architectureTests:test`. Expected: green.
- [ ] Manual (supervisor, spec "Verification"): `./gradlew :desktop:run --args='--dev'` with a demo project, mouse-free: Mod+1..5, F6, arrows and Enter in the tree, the Esc ladder from an open scene (focus to tree, close, Home), Mod+N in each module, Mod+K over focus mode, F1, Mod+W on a dirty note. Android emulator with a hardware keyboard via `/android-cli`: module jumps, save all, sync. iOS: spec test only unless a simulator is available.
- [ ] Supervisor commits.
- [ ] Supervisor runs `/code-review` on the commit; findings go to a fix agent; re-test and commit fixes.
