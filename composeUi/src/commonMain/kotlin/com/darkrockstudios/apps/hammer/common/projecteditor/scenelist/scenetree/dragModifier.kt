package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

internal fun dragModifier(data: DragModifierData): Modifier {
    val draggable: Modifier
    data.apply {
        draggable = Modifier
            .onGloballyPositioned { coordinates ->
                data.nodeLayouts()[childNode.value.id] = coordinates
            }
            .pointerInput(count) {
                detectDragGestures(
                    onDragStart = {
                        startDragging(childNode.value.id)
                    },
                    onDragEnd = {
                        stopDragging()
                    }
                ) { change, dragAmount ->
                    if (childNode.value.id == selectedId()) {
                        setOffsetY(dragAmount.y)
                    }

                    val containerInfo = containerLayoutInfo()
                    val columnInfo = columnLayoutInfo()
                    if (containerInfo != null && columnInfo != null) {
                        val insertPosition = findInsertPosition(
                            change.position,
                            nodeLayouts(),
                            collapsedNodes,
                            summary().sceneTree,
                            selectedId(),
                            columnInfo
                        )

                        setInsertAt(insertPosition)

                        // Auto scroll test
                        nodeLayouts()[childNode.value.id]?.let { layout ->
                            val windowPos = layout.localToWindow(change.position)
                            val localPos = containerInfo.windowToLocal(windowPos)

                            val bottomTenPercent = containerInfo.size.height * .9f
                            val topTenPercent = containerInfo.size.height * .1f
                            if (localPos.y >= bottomTenPercent) {
                                autoScroll(containerInfo, false)
                            } else if (localPos.y <= topTenPercent) {
                                autoScroll(containerInfo, true)
                            }
                        }
                    }
                }
            }
            .offset {
                if ((childNode.value.id == selectedId())) {
                    IntOffset(
                        x = 0,
                        y = offsetY().roundToInt() + (scrollState().value - initialScroll())
                    )
                } else {
                    IntOffset.Zero
                }
            }
            .zIndex(if ((childNode.value.id == selectedId())) 2f else 1f)
            .alpha(if ((childNode.value.id == selectedId())) 0.5f else 1f)
    }
    return draggable
}