package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.Scene

@Composable
fun SceneListUi(
    component: SceneList,
    modifier: Modifier = Modifier
) {
    val state by component.state.subscribeAsState()
    var newSceneNameText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = newSceneNameText,
            onValueChange = { newSceneNameText = it },
            label = { Text("New Scene Name") }
        )
        ExtendedFloatingActionButton(
            onClick = {
                component.createScene(newSceneNameText)
                newSceneNameText = ""
            },
            text = { Text("Create Scene") },
            icon = { Icon(Icons.Filled.Add, "") },
            modifier = Modifier.align(alignment = Alignment.End).padding(top = Ui.PADDING)
        )
        LazyColumn(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            contentPadding = PaddingValues(Ui.PADDING)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\uD83D\uDCDD Scenes:",
                        style = MaterialTheme.typography.h5,
                    )
                }
            }
            items(state.scenes.size) { index ->
                val scene = state.scenes[index]
                val isSelected = scene == state.selectedScene
                SceneItem(scene, isSelected, component::onSceneSelected)
            }
        }
    }
}

@Composable
fun SceneItem(scene: Scene, isSelected: Boolean, onSceneSelected: (Scene) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Ui.PADDING)
            .run { if (isSelected) background(color = selectionColor()) else this }
            .clickable { onSceneSelected(scene) },
        elevation = Ui.ELEVATION
    ) {
        Column(modifier = Modifier.padding(Ui.PADDING)) {
            Text(
                "Scene: ${scene.scene}",
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun selectionColor(): Color =
    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)