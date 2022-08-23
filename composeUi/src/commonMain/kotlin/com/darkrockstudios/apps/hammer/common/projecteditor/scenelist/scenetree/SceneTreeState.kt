package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberReorderableLazyListState(
    summary: SceneSummary,
    moveItem: (moveRequest: MoveRequest) -> Unit,
): SceneTreeState {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    return remember {
        SceneTreeState(
            sceneSummary = summary,
            moveItem = moveItem,
            coroutineScope = coroutineScope,
            listState = listState,
        )
    }
}

class SceneTreeState(
    sceneSummary: SceneSummary,
    val moveItem: (moveRequest: MoveRequest) -> Unit,
    val coroutineScope: CoroutineScope,
    val listState: LazyListState,
) {
    internal var summary by mutableStateOf(sceneSummary)
    var selectedId by mutableStateOf(-1)
    var insertAt by mutableStateOf<InsertPosition?>(null)
    val collapsedNodes = mutableStateMapOf<Int, Boolean>()
    var dragId by mutableStateOf(0)

    private var scrollJob by mutableStateOf<Job?>(null)
    private var treeHash by mutableStateOf(sceneSummary.sceneTree.hashCode())

    fun getTree() = summary.sceneTree

    fun updateSummary(sceneSummary: SceneSummary) {
        summary = sceneSummary
        cleanUpOnDelete()
    }

    private fun cleanUpOnDelete() {
        val newHash = summary.sceneTree.hashCode()
        if (treeHash != newHash) {
            treeHash = newHash
            dragId += 1

            // Prune layouts if the id is not found in the tree
            val nodeIt = collapsedNodes.iterator()
            while (nodeIt.hasNext()) {
                val (id, _) = nodeIt.next()
                val foundNode = summary.sceneTree.findBy { it.id == id }
                if (foundNode == null) {
                    nodeIt.remove()
                }
            }
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
        val previousIndex = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
        if (scrollJob?.isActive != true) {
            scrollJob = if (up) {
                if (previousIndex > 0) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(previousIndex)
                    }
                } else {
                    null
                }
            } else {
                coroutineScope.launch {
                    listState.layoutInfo.apply {
                        val viewportHeight = viewportEndOffset + viewportStartOffset
                        val index = visibleItemsInfo.size + previousIndex
                        val lastInfo = visibleItemsInfo[visibleItemsInfo.size - 1]
                        val offset = lastInfo.size - viewportHeight

                        listState.animateScrollToItem(index, offset)
                    }
                }
            }
        }
    }

    fun startDragging(id: Int) {
        if (selectedId == -1) {
            selectedId = id
        }
    }

    fun stopDragging() {
        val insertPosition = insertAt
        val selectedIndex = summary.sceneTree.indexOf { it.id == selectedId }
        if (selectedIndex > 0 && insertPosition != null) {
            val request = MoveRequest(
                selectedId,
                insertPosition
            )
            moveItem(request)
        }

        selectedId = -1
        insertAt = null
        dragId += 1
    }

    fun toggleExpanded(nodeId: Int) {
        val collapse = !(collapsedNodes[nodeId] ?: false)
        collapsedNodes[nodeId] = collapse
    }
}
