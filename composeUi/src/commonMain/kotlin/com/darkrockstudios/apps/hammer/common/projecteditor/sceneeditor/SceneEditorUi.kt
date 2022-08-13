package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.painterResource
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.richtexteditor.model.RichTextValue
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle

private fun getInitialContent(sceneContent: SceneContent?): RichTextValue {
    return if (sceneContent != null) {
        val composeText = sceneContent.platformRepresentation as? ComposeRichText
        val markdown = sceneContent.markdown
        if (composeText != null) {
            RichTextValue.fromSnapshot(composeText.snapshot)
        } else if (markdown != null) {
            RichTextValue.fromSnapshot(markdown.markdownToSnapshot())
        } else {
            throw IllegalStateException("Should be impossible to not have either platform rep or markdown")
        }
    } else {
        RichTextValue.get()
    }
}

@Composable
fun SceneEditorUi(
    component: SceneEditor,
    modifier: Modifier = Modifier,
    drawableKlass: Any? = null
) {
    val state by component.state.subscribeAsState()
    val lastDiscarded by component.lastDiscarded.subscribeAsState()

    var sceneText by remember {
        mutableStateOf(
            getInitialContent(state.sceneBuffer?.content)
        )
    }

    LaunchedEffect(lastDiscarded) {
        sceneText = getInitialContent(state.sceneBuffer?.content)
    }

    Column(modifier = modifier) {
        Row {
            if (state.isEditingName) {
                var editSceneNameValue by remember { mutableStateOf(state.sceneItem.name) }

                TextField(
                    value = editSceneNameValue,
                    onValueChange = { editSceneNameValue = it },
                    modifier = Modifier.padding(Ui.PADDING),
                    label = { Text("Scene Name") }
                )
                Button(onClick = { component.changeSceneName(editSceneNameValue) }) {
                    Text("Save")
                }
                Button(onClick = component::endSceneNameEdit) {
                    Text("Cancel")
                }
            } else {
                ClickableText(
                    AnnotatedString("Scene: ${state.sceneItem.name}"),
                    modifier = Modifier.padding(Ui.PADDING),
                    onClick = { component.beginSceneNameEdit() }
                )
            }
            if (state.sceneBuffer?.dirty == true) {
                Text("Unsaved", modifier = Modifier.padding(Ui.PADDING))
                Button(onClick = { component.storeSceneContent() }) {
                    Text("Save")
                }
            }
        }
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
                drawableKlass = drawableKlass,
                active = sceneText.isUndoAvailable
            ) {
                sceneText = sceneText.undo()
            }
            EditorAction(
                iconRes = "drawable/icon_redo.xml",
                drawableKlass = drawableKlass,
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
                component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
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