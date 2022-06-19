package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.data.text.markdownToAnnotatedString
import com.darkrockstudios.apps.hammer.common.data.text.toMarkdown

@Composable
fun SceneEditorUi(
    component: SceneEditor,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    val textFieldValueState = remember {
        mutableStateOf(
            TextFieldValue(
                annotatedString = state.sceneContent?.markdownToAnnotatedString()
                    ?: AnnotatedString("")
            )
        )
    }

    Column(modifier = modifier) {
        Text("Scene: ${state.scene.name}")
        Row {
            Button({
                textFieldValueState.value = TextFieldValue(bold(textFieldValueState.value))
            }) {
                Text("B")
            }
        }
        BasicTextField(
            modifier = Modifier.fillMaxSize(),
            value = textFieldValueState.value,
            enabled = state.sceneContent != null,
            onValueChange = { tfv ->
                textFieldValueState.value = tfv
                component.onContentChanged(tfv.annotatedString.toMarkdown())
            },
        )
    }
}

private fun bold(value: TextFieldValue): AnnotatedString {
    return if (!value.selection.collapsed) {
        val start = value.selection.start
        val end = value.selection.end

        val builder = AnnotatedString.Builder(value.annotatedString)
        builder.addStyle(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            start = start,
            end = end
        )
        builder.toAnnotatedString()
    } else {
        value.annotatedString
    }
}