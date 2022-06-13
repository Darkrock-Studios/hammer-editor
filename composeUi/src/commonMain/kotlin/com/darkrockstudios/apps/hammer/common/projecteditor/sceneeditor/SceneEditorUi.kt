package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun SceneEditorUi(
    component: SceneEditor,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val focusRequester = remember { FocusRequester() }

    val textFieldValueState = remember {
        mutableStateOf(
            TextFieldValue(
                text = state.sceneContent ?: "",
            )
        )
    }

    Column(modifier = modifier) {
        Text("Scene: ${state.scene.name}")
        BasicTextField(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
            value = textFieldValueState.value,
            enabled = state.sceneContent != null,
            onValueChange = { tfv ->
                textFieldValueState.value = tfv
                component.onContentChanged(tfv.text)
            },
        )
    }

    SideEffect {
        focusRequester.requestFocus()
    }
}