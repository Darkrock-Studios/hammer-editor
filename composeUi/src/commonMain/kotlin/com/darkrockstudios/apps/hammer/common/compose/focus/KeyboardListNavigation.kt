package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * How Up and Down move in a grid: the item a row below ([down]) or above [current], among
 * [count] items. Left and Right stay single steps.
 */
fun interface GridRowStep {
	fun step(current: Int, count: Int, down: Boolean): Int
}

/** The cursor after [key] in a list of [count], or null when [key] does not move it. */
internal fun listCursorStep(current: Int, count: Int, key: Key, rowStep: GridRowStep? = null): Int? {
	val vertical = key == Key.DirectionDown || key == Key.DirectionUp
	val horizontal = rowStep != null && (key == Key.DirectionRight || key == Key.DirectionLeft)
	if (!vertical && !horizontal && key != Key.MoveHome && key != Key.MoveEnd) return null
	if (count <= 0) return null
	val last = count - 1
	val at = current.coerceAtMost(last)
	return when {
		key == Key.MoveHome -> 0
		key == Key.MoveEnd -> last
		at < 0 -> 0
		vertical && rowStep != null -> rowStep.step(at, count, key == Key.DirectionDown).coerceIn(0, last)
		key == Key.DirectionDown || key == Key.DirectionRight -> (at + 1).coerceAtMost(last)
		else -> (at - 1).coerceAtLeast(0)
	}
}

/**
 * In a grid of [columns] equal cells, the item a row below or above [current]. Down from a row
 * with nothing under it lands on the last item when a shorter last row is still below.
 */
internal fun gridRowStep(current: Int, count: Int, columns: Int, down: Boolean): Int {
	val perRow = columns.coerceAtLeast(1)
	val last = count - 1
	if (!down) return (current - perRow).takeIf { it >= 0 } ?: current
	val below = current + perRow
	return when {
		below <= last -> below
		current / perRow < last / perRow -> last
		else -> current
	}
}

/** A staggered grid item on screen: its [lane] and the y of its [top]. */
internal data class LaneCell(val index: Int, val lane: Int, val top: Int)

/**
 * In a staggered grid, the nearest item below or above [current] in its lane, from the items on
 * screen. When the lane has none on screen: if every item that way is on screen the lane ends
 * there, so the nearest item below or above in any lane, else one step, as the lane's next item
 * may be off screen.
 */
internal fun staggeredRowStep(current: Int, count: Int, down: Boolean, shown: List<LaneCell>): Int {
	val step = if (down) (current + 1).coerceAtMost(count - 1) else (current - 1).coerceAtLeast(0)
	val here = shown.firstOrNull { it.index == current } ?: return step
	val ahead = shown.filter { if (down) it.index > current else it.index < current }
	val inLane = ahead.filter { it.lane == here.lane }
	if (inLane.isNotEmpty()) return if (down) inLane.minOf { it.index } else inLane.maxOf { it.index }
	val remaining = if (down) count - 1 - current else current
	if (ahead.size < remaining) return step
	return ahead
		.filter { if (down) it.top > here.top else it.top < here.top }
		.minWithOrNull(compareBy({ abs(it.top - here.top) }, { abs(it.lane - here.lane) }))
		?.index ?: current
}

/** Up and Down by row, with the column count read from the laid out items. */
fun LazyGridState.gridRowStep(): GridRowStep = GridRowStep { current, count, down ->
	val columns = layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }?.plus(1) ?: 1
	gridRowStep(current, count, columns, down)
}

/** Up and Down within a lane, from the laid out items. */
fun LazyStaggeredGridState.laneRowStep(): GridRowStep = GridRowStep { current, count, down ->
	val shown = layoutInfo.visibleItemsInfo.map { LaneCell(it.index, it.lane, it.offset.y) }
	staggeredRowStep(current, count, down, shown)
}

/**
 * A list's roving cursor. It holds the key of the item under it, so filtering or sorting keeps
 * it on the same item; when that item goes, it falls to where the item was.
 */
@Stable
class ListCursorState internal constructor(
	private val savedKey: MutableState<Any?>,
	private val savedIndex: MutableIntState,
) {
	/** The key of the item under the cursor, null before the first move. */
	val key: Any?
		get() = savedKey.value

	/** The cursor's index among [keys], or -1 when it has no item there. */
	fun indexIn(keys: List<Any>): Int {
		val key = savedKey.value ?: return -1
		if (keys.isEmpty()) return -1
		val at = keys.indexOf(key)
		return if (at >= 0) at else savedIndex.intValue.coerceIn(0, keys.lastIndex)
	}

	internal fun moveTo(index: Int, keys: List<Any>) {
		savedKey.value = keys[index]
		savedIndex.intValue = index
	}

	/** Puts the cursor on the item with [key], wherever the list shows it. */
	internal fun placeOn(key: Any) {
		savedKey.value = key
	}

	internal fun moveToKey(key: Any) {
		val index = shownKeys.indexOf(key)
		if (index >= 0) moveTo(index, shownKeys)
	}

	internal var hasFocus by mutableStateOf(false)

	internal var shownKeys: List<Any> = emptyList()

	internal val focusRequester = FocusRequester()
}

@Composable
fun rememberListCursorState(): ListCursorState {
	val savedKey = rememberSaveable { mutableStateOf<Any?>(null) }
	val savedIndex = rememberSaveable { mutableIntStateOf(-1) }
	return remember(savedKey, savedIndex) { ListCursorState(savedKey, savedIndex) }
}

/**
 * Whether the item with [key] draws the cursor: only while the list has keyboard focus. Derived
 * per item, so a cursor move recomposes only the items it leaves and lands on.
 */
@Composable
fun ListCursorState.showsCursorOn(key: Any): Boolean {
	val inputModeManager = LocalInputModeManager.current
	val shown by remember(this, key, inputModeManager) {
		derivedStateOf {
			hasFocus && this@showsCursorOn.key == key && inputModeManager.inputMode == InputMode.Keyboard
		}
	}
	return shown
}

/**
 * Makes the list the single focus stop for its rows: Up/Down, Home and End move [state]'s cursor,
 * Enter activates the row under it. With [rowStep] the list is a grid: Up/Down go by row and
 * Left/Right step one item. Keys only count while the list itself has focus, not a row's own
 * control.
 *
 * [keys] are the shown items' keys in order. The cursor saves its key, so keys must be saveable
 * (an Int or a String).
 */
@Composable
fun Modifier.keyboardListNavigation(
	state: ListCursorState,
	keys: List<Any>,
	onActivate: (Int) -> Unit,
	scrollToItem: suspend (Int) -> Unit,
	rowStep: GridRowStep? = null,
): Modifier {
	val scope = rememberCoroutineScope()
	val inputModeManager = LocalInputModeManager.current
	val activate by rememberUpdatedState(onActivate)
	val scroll by rememberUpdatedState(scrollToItem)
	val currentKeys by rememberUpdatedState(keys)
	val currentRowStep by rememberUpdatedState(rowStep)
	LaunchedEffect(state, keys) {
		state.shownKeys = keys
		val index = state.indexIn(keys)
		if (index >= 0) state.moveTo(index, keys)
	}
	return this
		.focusRequester(state.focusRequester)
		.onFocusChanged { state.hasFocus = it.isFocused }
		.onKeyEvent { event ->
			if (!state.hasFocus || !event.isBareKeyDown()) return@onKeyEvent false
			val shown = currentKeys
			val current = state.indexIn(shown)
			if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
				if (current < 0) return@onKeyEvent false
				activate(current)
				return@onKeyEvent true
			}
			val next = listCursorStep(current, shown.size, event.key, currentRowStep)
				?: return@onKeyEvent false
			inputModeManager.markKeyboardInput()
			state.moveTo(next, shown)
			scope.launch { scroll(next) }
			true
		}
		.focusable()
}

/**
 * Switches to keyboard input mode, which shows the cursor. Compose desktop enters touch mode on
 * any mouse press and leaves it only for a focus key nothing handled, so a list that handles its
 * own arrows, or F6, has to say so.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun InputModeManager.markKeyboardInput() {
	requestInputMode(InputMode.Keyboard)
}

internal fun KeyEvent.isBareKeyDown(): Boolean =
	type == KeyEventType.KeyDown && !isCtrlPressed && !isMetaPressed && !isAltPressed && !isShiftPressed

/** Set on the item drawing [listCursorFrame], so tests can find the cursor without reading pixels. */
val ListCursorFrame = SemanticsPropertyKey<Unit>("ListCursorFrame")

/** The frame a card under a list cursor draws: a hairline in ink, as a selected cell's. */
@Composable
fun Modifier.listCursorFrame(shown: Boolean): Modifier =
	if (shown) {
		border(Dp.Hairline, MaterialTheme.colorScheme.onSurface, RectangleShape)
			.semantics { this[ListCursorFrame] = Unit }
	} else {
		this
	}

/**
 * An item of a [keyboardListNavigation] list: it draws the cursor's frame, and a click on it also
 * puts the cursor here and focuses the list, so the arrows carry on from the clicked item.
 */
@Composable
fun Modifier.listCursorItem(state: ListCursorState, key: Any): Modifier =
	listCursorFrame(state.showsCursorOn(key))
		.onKeyboardPointerPress {
			state.moveToKey(key)
			state.focusRequester.requestFocus()
		}

/**
 * Calls [onPress] when a mouse's primary button goes down here, or a touch while a hardware
 * keyboard is attached. A bare touch screen is left alone: touch mode shows no keyboard cursor.
 * Observes without consuming, so the item's own click and drag handling still run.
 */
@Composable
fun Modifier.onKeyboardPointerPress(onPress: () -> Unit): Modifier {
	val keyboardAttached by rememberUpdatedState(hasHardwareKeyboard())
	val press by rememberUpdatedState(onPress)
	return pointerInput(Unit) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false)
			val counts = when (down.type) {
				PointerType.Mouse -> currentEvent.buttons.isPrimaryPressed
				else -> keyboardAttached
			}
			if (counts) press()
		}
	}
}

/** Calls [onPress] on any pointer press here, seen before the content handles it and never consumed. */
fun Modifier.onAnyPointerPress(key: Any?, onPress: () -> Unit): Modifier =
	pointerInput(key) {
		awaitPointerEventScope {
			while (true) {
				if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onPress()
			}
		}
	}

/**
 * Scrolls the least distance that shows item [index] whole above any end padding. [key] guards
 * against a layout that has not caught up with a change to the list yet. An item off screen is
 * scrolled to on the next measure: scrolling now would measure items whose composition is still
 * pending (rows a reveal just expanded), which Compose refuses.
 */
internal suspend fun LazyListState.revealItem(index: Int, key: Any? = null) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index && (key == null || it.key == key) }
	if (item == null) {
		requestScrollToItem(index)
		return
	}
	val end = info.viewportEndOffset - info.afterContentPadding
	revealSpan(item.offset, item.offset + item.size, end)
}

internal suspend fun LazyGridState.revealItem(index: Int) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index }
	revealCell(index, item?.offset, item?.size, info.viewportEndOffset - info.afterContentPadding) { scrollToItem(it) }
}

internal suspend fun LazyStaggeredGridState.revealItem(index: Int) {
	val info = layoutInfo
	val item = info.visibleItemsInfo.firstOrNull { it.index == index }
	revealCell(index, item?.offset, item?.size, info.viewportEndOffset - info.afterContentPadding) { scrollToItem(it) }
}

/** A grid's reveal of the cell at [offset] and [size], both null while it is off screen. */
private suspend fun ScrollableState.revealCell(
	index: Int,
	offset: IntOffset?,
	size: IntSize?,
	end: Int,
	scrollToItem: suspend (Int) -> Unit,
) {
	if (offset == null || size == null) {
		scrollToItem(index)
		return
	}
	revealSpan(offset.y, offset.y + size.height, end)
}

private suspend fun ScrollableState.revealSpan(top: Int, bottom: Int, end: Int) {
	when {
		top < 0 -> scrollBy(top.toFloat())
		bottom > end -> scrollBy((bottom - end).coerceAtMost(top).toFloat())
	}
}
