package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/** The cursor after [key] in a list of [count], or null when [key] does not move it. */
internal fun listCursorStep(current: Int, count: Int, key: Key, horizontalSteps: Boolean): Int? {
	val forward = key == Key.DirectionDown || (horizontalSteps && key == Key.DirectionRight)
	val back = key == Key.DirectionUp || (horizontalSteps && key == Key.DirectionLeft)
	if (!forward && !back && key != Key.MoveHome && key != Key.MoveEnd) return null
	if (count <= 0) return null
	val last = count - 1
	val at = current.coerceAtMost(last)
	return when {
		key == Key.MoveHome -> 0
		key == Key.MoveEnd -> last
		at < 0 -> 0
		forward -> (at + 1).coerceAtMost(last)
		else -> (at - 1).coerceAtLeast(0)
	}
}

/** A list's roving cursor: an index into what the list shows, or -1 before the first move. */
@Stable
class ListCursorState internal constructor(private val saved: MutableIntState) {
	var cursor: Int
		get() = saved.intValue
		set(value) {
			saved.intValue = value
		}

	internal var hasFocus by mutableStateOf(false)
}

@Composable
fun rememberListCursorState(): ListCursorState {
	val saved = rememberSaveable { mutableIntStateOf(-1) }
	return remember(saved) { ListCursorState(saved) }
}

/** Whether row [index] draws the cursor: only while the list has keyboard focus. */
@Composable
fun ListCursorState.showsCursorAt(index: Int): Boolean =
	hasFocus && cursor == index && LocalInputModeManager.current.inputMode == InputMode.Keyboard

/**
 * Makes the list the single focus stop for its rows: Up/Down (and Left/Right with
 * [horizontalSteps]), Home and End move [state]'s cursor, Enter activates the row under it. Keys
 * only count while the list itself has focus, not a row's own control.
 */
@Composable
fun Modifier.keyboardListNavigation(
	state: ListCursorState,
	itemCount: Int,
	onActivate: (Int) -> Unit,
	scrollToItem: suspend (Int) -> Unit,
	horizontalSteps: Boolean = false,
): Modifier {
	val scope = rememberCoroutineScope()
	val activate by rememberUpdatedState(onActivate)
	val scroll by rememberUpdatedState(scrollToItem)
	LaunchedEffect(state, itemCount) {
		if (itemCount > 0 && state.cursor >= itemCount) state.cursor = itemCount - 1
	}
	return this
		.onFocusChanged { state.hasFocus = it.isFocused }
		.onKeyEvent { event ->
			if (!state.hasFocus || !event.isBareKeyDown()) return@onKeyEvent false
			if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
				val index = state.cursor.coerceAtMost(itemCount - 1)
				if (index < 0) return@onKeyEvent false
				activate(index)
				return@onKeyEvent true
			}
			val next = listCursorStep(state.cursor, itemCount, event.key, horizontalSteps)
				?: return@onKeyEvent false
			state.cursor = next
			scope.launch { scroll(next) }
			true
		}
		.focusable()
}

internal fun KeyEvent.isBareKeyDown(): Boolean =
	type == KeyEventType.KeyDown && !isCtrlPressed && !isMetaPressed && !isAltPressed && !isShiftPressed

/** The frame a card under a list cursor draws: a hairline in ink, as a selected cell's. */
@Composable
fun Modifier.listCursorFrame(shown: Boolean): Modifier =
	if (shown) border(Dp.Hairline, MaterialTheme.colorScheme.onSurface, RectangleShape) else this

/**
 * Scrolls the least distance that shows item [index] whole above any end padding. [key] guards
 * against a layout that has not caught up with a change to the list yet.
 */
internal suspend fun LazyListState.revealItem(index: Int, key: Any? = null) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index && (key == null || it.key == key) }
	if (item == null) {
		scrollToItem(index)
		return
	}
	val end = info.viewportEndOffset - info.afterContentPadding
	revealSpan(item.offset, item.offset + item.size, end)
}

internal suspend fun LazyGridState.revealItem(index: Int) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index }
	if (item == null) {
		scrollToItem(index)
		return
	}
	val end = info.viewportEndOffset - info.afterContentPadding
	revealSpan(item.offset.y, item.offset.y + item.size.height, end)
}

internal suspend fun LazyStaggeredGridState.revealItem(index: Int) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index }
	if (item == null) {
		scrollToItem(index)
		return
	}
	val end = info.viewportEndOffset - info.afterContentPadding
	revealSpan(item.offset.y, item.offset.y + item.size.height, end)
}

private suspend fun ScrollableState.revealSpan(top: Int, bottom: Int, end: Int) {
	when {
		top < 0 -> scrollBy(top.toFloat())
		bottom > end -> scrollBy((bottom - end).coerceAtMost(top).toFloat())
	}
}
