package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.TreeValue


@Composable
fun SceneTree(
    summary: SceneSummary?,
    modifier: Modifier
) {
    summary ?: return
    summary.sceneTree

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawLine(
            start = Offset(x = canvasWidth, y = 0f),
            end = Offset(x = 0f, y = canvasHeight),
            color = Color.Blue
        )
    }

    SceneTreeNode(summary.sceneTree.root, Modifier.fillMaxWidth())
}

@Composable
fun SceneTreeNode(
    tree: TreeValue<SceneItem>,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        drawLine(color = Color.Blue, start = Offset(0f, 0f), end = Offset(width, 0f))
    }
    Text(tree.value.name)

    tree.children.forEach { child ->
        SceneTreeNode(
            child,
            modifier.padding(start = Ui.PADDING)
        )
    }
}