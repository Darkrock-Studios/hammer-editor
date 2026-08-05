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

| Shortcut     | Action                                       | Source                                           |
|--------------|----------------------------------------------|--------------------------------------------------|
| `Ctrl+Alt+S` | Save all dirty buffers (scenes, notes, etc.) | `saveAllShortcutModifier` via `ProjectRootUi.kt` |
| `F3`         | Start project sync                           | `syncShortcutModifier` via `ProjectRootUi.kt`    |

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
