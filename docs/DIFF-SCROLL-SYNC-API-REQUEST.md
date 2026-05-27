# Scroll-sync API request: `composetexteditor`

The Draft Compare and (eventually) Scene Conflict Merge UIs would benefit from
**synchronized scrolling** between two `TextEditor` panes. The prose diff
module (`base/.../diff/`) already produces matched anchor pairs
(`DiffAnchor(leftSource, rightSource)`) at every change boundary, so given a
position on one side we can compute the corresponding position on the other.

What's blocking us from wiring it up: `composetexteditor` 2.0.4 doesn't expose
a way to read **which `CharLineOffset` is currently at the top of the
viewport** for an editor. We can drive the destination side fine, but we can't
observe the source side without that.

## What we have today (sufficient destination-side API)

```kotlin
textEditorState.scrollState.value                              // current scroll Y in px, Compose-observable
textEditorState.scrollState.maxValue
textEditorState.scrollManager.scrollToPosition(offset: CharLineOffset)
textEditorState.scrollManager.scrollToPosition(positionPx: Int, animated: Boolean)
```

## What we need (source-side observation)

Either of the following would unblock anchor-aware sync — listed in order of
preference:

### Option A — Public top-visible offset (preferred)

```kotlin
val TextEditorState.firstVisibleOffset: CharLineOffset   // Compose-observable
```

…or as a method on the scroll manager:

```kotlin
fun TextEditorScrollManager.firstVisibleOffset(): CharLineOffset
```

Returns the `(line, char)` that currently sits at the top of the viewport (or
the closest before, if the top falls inside a wrapped line). Composable
observers should recompose when the user scrolls.

### Option B — Public Y-position helpers

Promote the existing `@VisibleForTesting internal` helpers to public:

```kotlin
fun TextEditorScrollManager.calculateOffsetYPosition(offset: CharLineOffset): Float
fun TextEditorScrollManager.offsetAtYPosition(y: Float): CharLineOffset
```

Either one is enough — Hammer can derive the other.

## Why both isn't enough alone

`scrollToPosition(CharLineOffset)` adjusts scroll so the offset is *visible*,
not so it's *at the top*. For sync we want symmetric scroll-by-line, so the
destination call would need either:

- a `top: Boolean` parameter on `scrollToPosition(offset)` to force
  top-alignment, or
- the Y-position helper from Option B (so we can scroll by Y precisely).

## How Hammer would use it

```kotlin
// Build once from the diff result
class OffsetMap(anchors: List<DiffAnchor>) {
    fun leftToRight(leftOffset: Int): Int   // binary search + linear interpolation
    fun rightToLeft(rightOffset: Int): Int
}

// In the UI
LaunchedEffect(leftState) {
    snapshotFlow { leftState.firstVisibleOffset }
        .collect { leftOffset ->
            if (syncSource.value == Side.RIGHT) return@collect
            syncSource.value = Side.LEFT
            val rightCharOffset = offsetMap.leftToRight(leftSourceOffsetFor(leftOffset))
            rightState.scrollManager.scrollToPosition(rightState.charLineOffsetFor(rightCharOffset))
            syncSource.value = null
        }
}
// (mirror for right -> left)
```

The `syncSource` flag prevents feedback loops where programmatic scroll on the
opposite pane retriggers sync back.

## Where this lives in the codebase

`composeUi/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/storyeditor/drafts/DraftCompareUi.kt` —
the `ExpandedDraftCompareUi` row. Compact (tabbed) layout doesn't need sync.

The `DiffResult.anchors` list (in `base/.../diff/DiffTypes.kt`) is already
populated; only the editor-side observation piece is missing.
