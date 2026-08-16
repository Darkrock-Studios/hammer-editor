# Keyboard Shortcuts

Desktop, plus the two project shortcuts on Android and iOS with a hardware keyboard.
`Ctrl` and `Cmd` are interchangeable everywhere below: `onKeyShortcut`
(
`composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/ShortcutModifiers.kt`)
treats `isCtrlPressed || isMetaPressed` as a single "ctrl" modifier, so these are not
macOS-specific `Cmd` bindings, they work with either key on every platform.

## Application

These are matched in `desktop/.../WindowShortcuts.kt` and dispatched from each window's
`onKeyEvent`.

| Shortcut       | Action                | Window                                          |
|----------------|-----------------------|-------------------------------------------------|
| `Esc`          | Navigate back         | `ProjectEditorWindow.kt`, `ProjectSelectionWindow.kt` |
| `Ctrl+Q`       | Quit application      | `ProjectEditorWindow.kt`, `ProjectSelectionWindow.kt` |
| `Ctrl+W`       | Close current project | `ProjectEditorWindow.kt`                        |
| `Ctrl+Shift+F` | Open global search    | `ProjectEditorWindow.kt`                        |

Modifiers match exactly here too, which is what keeps `AltGr` (reported as `Ctrl+Alt` on
Windows) from quitting the app while a non-US layout types `@`.

## Project (while a project is open)

These two work on all three platforms, each through the host hook that does not depend on
what Compose has focused. Compose routes key events along the focus path, so a screen with
nothing focused never sees them, which is why none of these are `Modifier` shortcuts.

| Platform | Hook                                                          |
|----------|---------------------------------------------------------------|
| Desktop  | `ProjectEditorWindow.kt` window `onPreviewKeyEvent`           |
| Android  | `ProjectRootActivity.dispatchKeyEvent`                        |
| iOS      | `ShortcutHostController.keyCommands` in `ComposeContainer.swift` |

Android and iOS reach the shared action through `ProjectShortcutHost`, which
`ProjectRootScaffold` binds while the project UI is composed. The application shortcuts
above use the window's `onKeyEvent` instead and yield to a focused component that consumes
the key. Modifier shortcuts (the editor ones below) only fire when focus sits inside the
composable they are attached to.

Modifiers match exactly: `F3` means F3 with nothing held, so `Ctrl+F3` and `Shift+F3` do
not start a sync. On iOS the save-all chord is `Cmd+Opt+S` or `Ctrl+Opt+S`.

| Shortcut     | Action                                       |
|--------------|----------------------------------------------|
| `Ctrl+Alt+S` | Save all dirty buffers (scenes, notes, etc.) |
| `F3`         | Start project sync (server-linked only)      |

## Editors (scene, note, timeline event, story idea)

| Shortcut | Action                | Source                                                                                                      |
|----------|-----------------------|-------------------------------------------------------------------------------------------------------------|
| `Ctrl+S` | Save the current item | `saveShortcutModifier` via `SceneEditorUi.kt`, `ViewNoteUi.kt`, `ViewTimeLineEventUi.kt`, `StoryIdeasUi.kt` |
| `Ctrl+F` | Open find bar         | `findShortcutModifier` via `SceneEditorUi.kt`, `FocusModeUi.kt`                                             |

## Rich Text Formatting

Available anywhere `MarkdownFormatBar.kt` is used (scene, note, and encyclopedia editors).

| Shortcut       | Action               |
|----------------|----------------------|
| `Ctrl+B`       | Toggle bold          |
| `Ctrl+I`       | Toggle italic        |
| `Ctrl+Shift+X` | Toggle strikethrough |
