package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
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
) {
    val state by component.state.subscribeAsState()
    var sceneText by remember {
        mutableStateOf(
            getInitialContent(state.sceneContent?.markdownToSnapshot())
        )
    }

    Column(modifier = modifier.padding(Ui.PADDING)) {
        Text("Scene: ${state.scene.name}")
        Row {
            EditorAction(
                //iconRes = R.drawable.icon_bold,
                active = sceneText.currentStyles.contains(Style.Bold)
            ) {
                sceneText = sceneText.insertStyle(Style.Bold)
            }
        }

        RichTextEditor(
            modifier = Modifier.fillMaxSize(),
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
    //@DrawableRes iconRes: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Text("B")
        /*Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = iconRes),
            tint = if (active) Color.White else Color.Black,
            contentDescription = null
        )*/
    }
}