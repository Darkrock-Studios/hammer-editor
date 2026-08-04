package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
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

	return remember {
		SceneTreeState(
			sceneSummary = summary,
			moveItem = moveItem,
			coroutineScope = coroutineScope,
			listState = listState,
			collapsedNodes = collapsedNodes
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
) {
	internal var summary by mutableStateOf(sceneSummary)
	var selectedId by mutableStateOf(NO_SELECTION)
	var selectedNode by mutableStateOf<TreeValue<SceneItem>?>(null)
	var insertAt by mutableStateOf<InsertPosition?>(null)

	/** The rows the tree renders, in display order — also the drag handler's id → node lookup. */
	val visibleNodes: List<TreeValue<SceneItem>> by derivedStateOf {
		visibleSceneNodes(summary.sceneTree, collapsedNodes)
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

	private val rows = mutableStateMapOf<Int, LayoutCoordinates>()

	/**
	 * Where the rows are actually drawn, which is what every drag decision must be made against.
	 *
	 * Thar be dragons: [LazyListLayoutInfo] looks like the obvious source and is not. Its offsets
	 * are where items are headed, so while a placement animation runs it reports a row at a slot
	 * it has not reached, and drags land on the neighbour instead (issue #837).
	 */
	internal val rowLayouts: Collection<RowLayout>
		get() = rows.entries.mapNotNull { (id, coords) ->
			if (!coords.isAttached) return@mapNotNull null

			RowLayout(
				id = id,
				top = coords.positionInRoot().y - listTopInRoot,
				height = coords.size.height.toFloat(),
			)
		}

	/** Root-space top of the list, so row positions share the drag gesture's coordinates. */
	internal var listTopInRoot: Float = 0f

	/**
	 * Held live rather than sampled: item placement animations move a row by translating its
	 * layer every frame, without a new layout pass, so a position read during
	 * [androidx.compose.ui.layout.onGloballyPositioned] is the row's destination. Asking the
	 * coordinates where they are at the moment of the drag sees through that translation.
	 */
	internal fun setRowCoordinates(id: Int, coordinates: LayoutCoordinates) {
		rows[id] = coordinates
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

	fun autoScroll(up: Boolean) {
		if (scrollJob?.isActive == true) return

		scrollJob = coroutineScope.launch {
			if (up) {
				val targetIndex = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
				listState.animateScrollToItem(targetIndex)
			} else {
				val visibleItems = listState.layoutInfo.visibleItemsInfo
				if (visibleItems.isNotEmpty()) {
					val nextIndex = listState.firstVisibleItemIndex + 1
					listState.animateScrollToItem(nextIndex)
				}
			}
		}
	}

	fun startDragging(id: Int) {
		if (selectedId == NO_SELECTION) {
			selectedId = id
			selectedNode = summary.sceneTree.findBy { it.id == id }
		}
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
		insertAt = null
	}

	fun toggleExpanded(nodeId: Int) {
		val collapse = !(collapsedNodes[nodeId] ?: false)
		collapsedNodes[nodeId] = collapse
	}

	companion object {
		const val NO_SELECTION = -1
	}
}
