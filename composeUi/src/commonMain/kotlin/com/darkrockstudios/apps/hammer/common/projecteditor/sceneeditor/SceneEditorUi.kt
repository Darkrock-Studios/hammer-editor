package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun SceneEditorUi(
    component: SceneEditor,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    Column(modifier = modifier) {
        Text("SceneEditorUi")
        Text("Scene: ${state.scene.scene}")
    }
}