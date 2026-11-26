package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.LayoutCoordinates
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.compose.focus.listCursorStep
import com.darkrockstudios.apps.hammer.common.compose.focus.revealItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val collapsedNotesSaver = Saver<SnapshotStateMap<Int, Boolean>, List<Pair<Int, Boolean>>>(
	save = { map ->
		map.toList()
	},
	restore = { saved ->
		val map = SnapshotStateMap<Int, Boolean>()
		saved.forEach { item ->
			map[item.first] = item.second
		}
		map
	}
)

@Composable
fun rememberReorderableLazyListState(
	summary: SceneSummary,
	moveItem: (moveRequest: MoveRequest) -> Unit,
): SceneTreeState {
	val coroutineScope = rememberCoroutineScope()
	val listState = rememberLazyListState()
	val collapsedNodes = rememberSaveable(saver = collapsedNotesSaver) { mutableStateMapOf() }
	val cursor = rememberSaveable { mutableIntStateOf(SceneTreeState.NO_SELECTION) }

	return remember {
		SceneTreeState(
			sceneSummary = summary,
			moveItem = moveItem,
			coroutineScope = coroutineScope,
			listState = listState,
			collapsedNodes = collapsedNodes,
			cursor = cursor,
		)
	}
}

@Stable
class SceneTreeState(
	sceneSummary: SceneSummary,
	val moveItem: (moveRequest: MoveRequest) -> Unit,
	val coroutineScope: CoroutineScope,
	val listState: LazyListState,
	val collapsedNodes: SnapshotStateMap<Int, Boolean>,
	cursor: MutableIntState = mutableIntStateOf(NO_SELECTION),
) {
	internal var summary by mutableStateOf(sceneSummary)
	var selectedId by mutableStateOf(NO_SELECTION)
	var selectedNode by mutableStateOf<TreeValue<SceneItem>?>(null)

	/** The keyboard cursor's row id, [NO_SELECTION] until the first move. */
	var cursorId by cursor

	/** The rows the tree renders, in display order — also the drag handler's id → node lookup. */
	val visibleNodes: List<TreeValue<SceneItem>> by derivedStateOf {
		visibleSceneNodes(summary.sceneTree, collapsedNodes)
	}

	var dragPosition by mutableStateOf<Offset?>(null)
		private set

	/**
	 * Derived rather than assigned from the drag handler, because the pointer is only half of it:
	 * autoscroll slides the rows out from under a finger that never moves and so never delivers
	 * another event. Recomputing whenever either side changes keeps the drop target under the
	 * finger, and [rowLayouts] re-reads on every placement pass to make that happen.
	 */
	val insertAt: InsertPosition? by derivedStateOf {
		val position = dragPosition ?: return@derivedStateOf null

		findInsertPosition(
			dragOffset = position,
			layouts = rowLayouts,
			collapsedGroups = collapsedNodes,
			tree = summary.sceneTree,
			visibleNodes = visibleNodes,
			selectedNode = selectedNode,
		)
	}

	/**
	 * Row this press started on, claimed by the row itself so it is the row the user aimed at.
	 * Deliberately not snapshot state: it changes on every press and must not recompose the tree.
	 */
	internal var pressedRowId: Int = NO_SELECTION
		private set

	private var pressedPointer: PointerId? = null

	/**
	 * Records [rowId] as the press target, but only for the first pointer down. The drag detector
	 * follows that same pointer, so later fingers must not be able to retarget the drag.
	 */
	internal fun claimPress(pointer: PointerId, rowId: Int): Boolean {
		if (pressedPointer != null) return false

		pressedPointer = pointer
		pressedRowId = rowId
		return true
	}

	internal fun releasePress(pointer: PointerId) {
		if (pressedPointer != pointer) return

		pressedPointer = null
		pressedRowId = NO_SELECTION
	}

	private data class RowEntry(val coordinates: LayoutCoordinates, val placement: Int)

	private val rows = mutableStateMapOf<Int, RowEntry>()
	private var placementCount = 0
	private var listCoordinates: LayoutCoordinates? = null

	/**
	 * Where the rows are actually drawn, in display order, which is what every drag decision has
	 * to be made against.
	 *
	 * Thar be dragons: [LazyListLayoutInfo] looks like the obvious source and is not. Its offsets
	 * are where items are headed, so while a placement animation runs it reports a row at a slot
	 * it has not reached, and drags land on the neighbour instead (issue #837).
	 */
	internal val rowLayouts: List<RowLayout>
		get() {
			val list = listCoordinates?.takeIf { it.isAttached } ?: return emptyList()

			return rows.entries.mapNotNull { (id, entry) ->
				if (!entry.coordinates.isAttached) return@mapNotNull null

				RowLayout(
					id = id,
					top = list.localPositionOf(entry.coordinates, Offset.Zero).y,
					height = entry.coordinates.size.height.toFloat(),
				)
			}.sortedBy { it.top }
		}

	internal fun setListCoordinates(coordinates: LayoutCoordinates) {
		listCoordinates = coordinates
	}

	/**
	 * Coordinates are held live rather than sampled: item placement animations move a row by
	 * translating its layer every frame, without a new layout pass, so a position read during
	 * [androidx.compose.ui.layout.onGloballyPositioned] is the row's destination rather than
	 * where it is drawn. Resolving them against the list at drag time sees through that.
	 *
	 * [RowEntry.placement] makes every call a distinct map value. Re-putting equal coordinates
	 * is a no-op the snapshot system does not report, which would leave the insert line with
	 * nothing to invalidate it as rows scroll.
	 */
	internal fun setRowCoordinates(id: Int, coordinates: LayoutCoordinates) {
		rows[id] = RowEntry(coordinates, placementCount++)
	}

	internal fun removeRowCoordinates(id: Int) {
		rows.remove(id)
	}

	private var scrollJob by mutableStateOf<Job?>(null)
	private var treeHash by mutableStateOf(sceneSummary.sceneTree.hashCode())

	fun getTree() = summary.sceneTree

	fun updateSummary(sceneSummary: SceneSummary) {
		if (summary != sceneSummary) {
			summary = sceneSummary
			cleanUpOnDelete()
		}
	}

	private fun cleanUpOnDelete() {
		val newHash = summary.sceneTree.hashCode()
		if (treeHash != newHash) {
			treeHash = newHash

			// Build set of valid IDs once for O(1) lookups
			val validIds = summary.sceneTree.mapTo(HashSet()) { it.value.id }

			// Prune collapsed nodes for deleted items
			collapsedNodes.keys.removeAll { it !in validIds }
		}
	}

	fun collapseAll() {
		summary.sceneTree
			.filter { it.value.type == SceneItem.Type.Group }
			.forEach { node ->
				collapsedNodes[node.value.id] = true
			}
	}

	fun expandAll() {
		collapsedNodes.clear()
	}

	/** True to scroll up, false to scroll down, null when the drag is clear of both edges. */
	private fun dragScrollDirection(): Boolean? {
		val position = dragPosition ?: return null
		val layoutInfo = listState.layoutInfo
		val height = layoutInfo.viewportSize.height - layoutInfo.viewportStartOffset
		if (height <= 0) return null

		return when {
			position.y >= height * AUTO_SCROLL_ZONE_END -> false
			position.y <= height * AUTO_SCROLL_ZONE_START -> true
			else -> null
		}
	}

	/**
	 * Scrolls a row at a time while the drag is held near either edge.
	 *
	 * The job keeps stepping on its own until the pointer leaves the edge or the list runs out,
	 * because a drag parked in the hot zone delivers no further pointer events to step it along.
	 */
	internal fun autoScrollForDrag() {
		if (scrollJob?.isActive == true) return
		if (dragScrollDirection() == null) return

		scrollJob = coroutineScope.launch {
			while (true) {
				val up = dragScrollDirection() ?: break
				if (up && !listState.canScrollBackward) break
				if (!up && !listState.canScrollForward) break

				val target = if (up) {
					(listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
				} else {
					listState.firstVisibleItemIndex + 1
				}
				listState.animateScrollToItem(target)
			}
		}
	}

	fun startDragging(id: Int) {
		if (selectedId == NO_SELECTION) {
			selectedId = id
			selectedNode = summary.sceneTree.findBy { it.id == id }
		}
	}

	internal fun updateDragPosition(position: Offset) {
		dragPosition = position
	}

	fun stopDragging() {
		val insertPosition = insertAt
		if (selectedId != ROOT_ID && insertPosition != null) {
			val request = MoveRequest(
				selectedId,
				insertPosition
			)
			moveItem(request)
		}

		selectedId = NO_SELECTION
		selectedNode = null
		dragPosition = null
		scrollJob?.cancel()
	}

	fun toggleExpanded(nodeId: Int) {
		val collapse = !(collapsedNodes[nodeId] ?: false)
		collapsedNodes[nodeId] = collapse
	}

	/** The cursor's row; a cursor hidden in a collapsed group stands on the group that hides it. */
	private fun cursorIndex(): Int {
		val nodes = visibleNodes
		val shown = nodes.indexOfFirst { it.value.id == cursorId }
		if (shown >= 0 || cursorId == NO_SELECTION) return shown
		val tree = summary.sceneTree
		val node = tree.findBy { it.id == cursorId } ?: return -1
		val ids = tree.getBranch(node.index, true).map { it.value.id }
		return ids.asReversed().firstNotNullOfOrNull { id ->
			nodes.indexOfFirst { it.value.id == id }.takeIf { it >= 0 }
		} ?: -1
	}

	internal fun cursorNode(): TreeValue<SceneItem>? = visibleNodes.getOrNull(cursorIndex())

	/** The row the cursor stands on, which is the hiding group's when its own row is collapsed away. */
	val cursorRowId: Int by derivedStateOf { cursorNode()?.value?.id ?: NO_SELECTION }

	private fun setCursorIndex(index: Int) {
		val node = visibleNodes.getOrNull(index) ?: return
		cursorId = node.value.id
		coroutineScope.launch { listState.revealItem(index, key = node.value.id) }
	}

	/** Up, Down, Home or End over the visible rows, as [listCursorStep] moves a list's cursor. */
	fun stepCursor(key: Key) {
		listCursorStep(cursorIndex(), visibleNodes.size, key)?.let(::setCursorIndex)
	}

	fun cursorToStart() = setCursorIndex(0)

	/** A collapsed group expands; an expanded group steps into its first child; a scene stays. */
	fun cursorRight() {
		val node = cursorNode() ?: return cursorToStart()
		if (!node.value.type.isCollection) return
		if (collapsedNodes[node.value.id] == true) {
			collapsedNodes[node.value.id] = false
		} else {
			node.children.firstOrNull()?.let { child ->
				setCursorIndex(visibleNodes.indexOfFirst { it.value.id == child.value.id })
			}
		}
	}

	/** An expanded group collapses; anything else steps out to its parent, never to the root. */
	fun cursorLeft() {
		val node = cursorNode() ?: return cursorToStart()
		if (node.value.type.isCollection && collapsedNodes[node.value.id] != true) {
			collapsedNodes[node.value.id] = true
			cursorId = node.value.id
			return
		}
		val parent = summary.sceneTree[node.parent]
		if (parent.value.isRootScene) return
		setCursorIndex(visibleNodes.indexOfFirst { it.value.id == parent.value.id })
	}

	/** Expands the groups around [id], puts the cursor on it and scrolls it into view. */
	fun revealCursor(id: Int) {
		val tree = summary.sceneTree
		val node = tree.findBy { it.id == id } ?: return
		tree.getBranch(node.index, excludeLeaf = true).forEach { collapsedNodes.remove(it.value.id) }
		setCursorIndex(visibleNodes.indexOfFirst { it.value.id == id })
	}

	private var openSceneId: Int? = null
	private var openSceneChanged = false

	internal fun setOpenScene(id: Int?) {
		if (id == openSceneId) return
		openSceneId = id
		openSceneChanged = true
	}

	/**
	 * Gives the focused tree a cursor: the open scene when the cursor has none yet, or when a
	 * scene was opened some other way (a next-scene chord) since; with no open scene and no
	 * cursor, the first row. Collapsed groups stay as they are.
	 */
	internal fun followOpenScene() {
		val open = openSceneId
		val followOpen = open != null && (cursorId == NO_SELECTION || openSceneChanged)
		openSceneChanged = false
		if (followOpen) {
			cursorId = open
		} else if (cursorIndex() >= 0) {
			return
		}
		val index = cursorIndex()
		if (index < 0) return cursorToStart()
		val node = visibleNodes[index]
		coroutineScope.launch { listState.revealItem(index, key = node.value.id) }
	}

	/**
	 * A click on row [id] puts the cursor there. A scene opened elsewhere before the click must
	 * not pull the cursor off the clicked row when the tree takes focus, so that is forgotten.
	 */
	internal fun cursorFromPointer(id: Int) {
		cursorId = id
		openSceneChanged = false
	}

	companion object {
		const val NO_SELECTION = -1

		private const val AUTO_SCROLL_ZONE_START = .1f
		private const val AUTO_SCROLL_ZONE_END = .9f
	}
}
