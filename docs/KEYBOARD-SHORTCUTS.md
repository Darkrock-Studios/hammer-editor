# Keyboard Shortcuts

Desktop-only. `Ctrl` and `Cmd` are interchangeable everywhere below: `onKeyShortcut`
(
`composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/compose/ShortcutModifiers.kt`)
treats `isCtrlPressed || isMetaPressed` as a single "ctrl" modifier, so these are not
macOS-specific `Cmd` bindings, they work with either key on every platform.

## Application

| Shortcut       | Action                | Source                                                               |
|----------------|-----------------------|----------------------------------------------------------------------|
| `Esc`          | Navigate back         | `ProjectEditorWindow.kt`, `ProjectSelectionWindow.kt` (`onKeyEvent`) |
| `Ctrl+Q`       | Quit application      | `ProjectEditorWindow.kt`, `ProjectSelectionWindow.kt` (`onKeyEvent`) |
| `Ctrl+W`       | Close current project | `ProjectEditorWindow.kt` (`onKeyEvent`)                              |
| `Ctrl+Shift+F` | Open global search    | `ProjectEditorWindow.kt` (`onKeyEvent`)                              |

## Project (while a project is open)

Both are handled on the window's `onPreviewKeyEvent`, which runs before the focus path,
so a focused editor cannot swallow them. The application shortcuts above use the
window's `onKeyEvent` instead and yield to a focused component that consumes the key.
Modifier shortcuts (the editor ones below) only fire when focus sits inside the
composable they are attached to, because Compose routes key events along the focus path.

Modifiers match exactly: `F3` means F3 with nothing held, so `Ctrl+F3` and `Shift+F3` do
not start a sync.

| Shortcut     | Action                                       | Source                                         |
|--------------|----------------------------------------------|------------------------------------------------|
| `Ctrl+Alt+S` | Save all dirty buffers (scenes, notes, etc.) | `ProjectEditorWindow.kt` (`onPreviewKeyEvent`) |
| `F3`         | Start project sync (server-linked only)      | `ProjectEditorWindow.kt` (`onPreviewKeyEvent`) |

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
