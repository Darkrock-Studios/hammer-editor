package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.painterResource
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.apps.hammer.common.data.text.toMarkdown
import com.darkrockstudios.richtexteditor.model.RichTextValue
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle
import com.darkrockstudios.richtexteditor.utils.RichTextValueSnapshot

private fun getInitialContent(snapshot: RichTextValueSnapshot?): RichTextValue {
    return snapshot?.let {
        RichTextValue.fromSnapshot(snapshot)
    } ?: RichTextValue.get()
}

@Composable
fun SceneEditorUi(
    component: SceneEditor,
    modifier: Modifier = Modifier,
    drawableKlass: Any? = null
) {
    val state by component.state.subscribeAsState()
    var sceneText by remember {
        mutableStateOf(
            getInitialContent(state.sceneContent?.markdownToSnapshot())
        )
    }

    Column(modifier = modifier) {
        Text("Scene: ${state.scene.name}", modifier = Modifier.padding(Ui.PADDING))
        Row(modifier = Modifier.fillMaxWidth().background(Color.Gray)) {
            EditorAction(
                iconRes = "drawable/icon_bold.xml",
                drawableKlass = drawableKlass,
                active = sceneText.currentStyles.contains(Style.Bold),
            ) {
                sceneText = sceneText.insertStyle(Style.Bold)
            }
            EditorAction(
                iconRes = "drawable/icon_italic.xml",
                drawableKlass = drawableKlass,
                active = sceneText.currentStyles.contains(Style.Italic),
            ) {
                sceneText = sceneText.insertStyle(Style.Italic)
            }
            EditorAction(
                iconRes = "drawable/icon_undo.xml",
                active = sceneText.isUndoAvailable
            ) {
                sceneText = sceneText.undo()
            }
            EditorAction(
                iconRes = "drawable/icon_redo.xml",
                active = sceneText.isRedoAvailable
            ) {
                sceneText = sceneText.redo()
            }
        }

        RichTextEditor(
            modifier = Modifier.fillMaxSize().padding(Ui.PADDING),
            value = sceneText,
            onValueChange = { rtv ->
                sceneText = rtv
                component.onContentChanged(rtv.getLastSnapshot().toMarkdown())
            },
            textFieldStyle = defaultRichTextFieldStyle().copy(
                placeholder = "My rich text editor in action",
                textColor = MaterialTheme.colors.onBackground,
                placeholderColor = MaterialTheme.colors.secondaryVariant,
            )
        )
    }
}

@Composable
private fun EditorAction(
    iconRes: String,
    drawableKlass: Any? = null,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(res = iconRes, drawableKlass = drawableKlass),
            tint = if (active) Color.White else Color.Black,
            contentDescription = null
        )
    }
}