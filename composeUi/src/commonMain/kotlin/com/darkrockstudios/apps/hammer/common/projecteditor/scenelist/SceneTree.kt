package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import io.github.aakira.napier.Napier
import kotlin.math.roundToInt


@Composable
fun SceneTree(
    summary: SceneSummary?,
    modifier: Modifier,
    itemUi: ItemUi
) {
    summary ?: return

    Napier.d("SceneTree")
    /*
    Canvas(modifier = modifier.wrapContentHeight()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawLine(
            start = Offset(x = canvasWidth, y = 0f),
            end = Offset(x = 0f, y = canvasHeight),
            color = Color.Blue
        )
    }
    */
    //val interactionSource = remember { MutableInteractionSource() }

    val scrollState = rememberScrollState()

    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }
    val startDragging = { id: Int ->
        Napier.d("startDragging")
        if (selectedId == -1) {
            selectedId = id
        }
    }
    val stopDragging = {
        Napier.d("stopDragging")
        selectedId = -1
        offsetY = 0F
    }

    var isDragging by remember { mutableStateOf(false) }

    /*
.draggable(
    orientation = Orientation.Horizontal,
    state = rememberDraggableState { delta ->
        offsetY += delta
        Napier.d("dragging: $offsetY")
        if (isDragging) {
            offsetY += delta
            Napier.d("is dragging: $offsetY")
        }
    }
)
*/
    //scrollState.scrollTo()

    Column(
        modifier = modifier
            .verticalScroll(scrollState),
    ) {
        summary.sceneTree.root.children.forEach { childNode ->

            var draggable = Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        startDragging(childNode.value.id)
                    },
                    onDragEnd = {
                        stopDragging()
                    }
                ) { _, dragAmount ->
                    if (selectedId == childNode.value.id) {
                        offsetY += dragAmount.y
                    }
                }
            }

            if (selectedId == childNode.value.id) {
                draggable = draggable.offset {
                    return@offset if (selectedId == childNode.value.id) {
                        Napier.d("Dragging: $offsetY")
                        IntOffset(x = 0, y = offsetY.roundToInt())
                    } else {
                        IntOffset(0, 0)
                    }
                }
            }

            SceneTreeNode(
                tree = childNode,
                dragModifier = draggable,
                itemUi = itemUi
            )
        }
    }
}

@Composable
fun SceneTreeNode(
    tree: TreeValue<SceneItem>,
    dragModifier: Modifier,
    itemUi: ItemUi
) {
    /*
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        drawLine(color = Color.Blue, start = Offset(0f, 0f), end = Offset(width, 0f))
    }
    */
    var collapsed by remember { mutableStateOf(false) }

    val toggleExpanded = {
        collapsed = !collapsed
    }

    itemUi(tree, toggleExpanded, dragModifier)

    AnimatedVisibility(visible = !collapsed) {
        tree.children.forEachIndexed { index, child ->
            SceneTreeNode(
                tree = child,
                dragModifier = dragModifier.padding(start = Ui.PADDING * index),
                itemUi = itemUi,
            )
        }
    }
}

typealias ItemUi = @Composable (
    node: TreeValue<SceneItem>,
    toggleExpanded: () -> Unit,
    draggable: Modifier,
) -> Unit