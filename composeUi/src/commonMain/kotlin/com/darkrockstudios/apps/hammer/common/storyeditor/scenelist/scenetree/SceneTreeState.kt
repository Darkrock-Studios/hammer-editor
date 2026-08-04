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
	 * Row this press started on, recorded by the rows themselves so it reflects the drawn
	 * position rather than a layout target.
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
