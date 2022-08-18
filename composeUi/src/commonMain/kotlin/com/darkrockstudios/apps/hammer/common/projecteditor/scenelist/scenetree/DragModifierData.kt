package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.layout.LayoutCoordinates
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

data class DragModifierData(
    val childNode: TreeValue<SceneItem>,
    val summary: () -> SceneSummary,
    val nodeLayouts: () -> HashMap<Int, LayoutCoordinates>,
    val columnLayoutInfo: () -> LayoutCoordinates?,
    val collapsedNodes: SnapshotStateMap<Int, Boolean>,
    val startDragging: (Int) -> Unit,
    val stopDragging: () -> Unit,
    val selectedId: () -> Int,
    val offsetY: () -> Float,
    val scrollState: () -> Int,
    val autoScroll: (container: LayoutCoordinates, up: Boolean) -> Unit,
    val initialScroll: () -> Int,
    val setOffsetY: (offset: Float) -> Unit,
    val count: Int,
    val setInsertAt: (insertAt: InsertPosition?) -> Unit,
    val containerLayoutInfo: () -> LayoutCoordinates?,
)