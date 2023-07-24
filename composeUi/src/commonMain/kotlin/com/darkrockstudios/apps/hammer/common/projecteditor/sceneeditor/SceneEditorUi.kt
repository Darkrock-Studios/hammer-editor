package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle
import kotlinx.coroutines.launch

@Composable
fun SceneEditorUi(
	component: SceneEditor,
	modifier: Modifier = Modifier,
	drawableKlass: Any? = null
) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()

	val snackbarHostState = remember { SnackbarHostState() }
	var sceneText by remember {
		mutableStateOf(
			getInitialEditorContent(state.sceneBuffer?.content)
		)
	}

	LaunchedEffect(lastForceUpdate) {
		sceneText = getInitialEditorContent(state.sceneBuffer?.content)
	}

	Toaster(state.toast, snackbarHostState)

	Box(modifier = modifier) {
		Column(
			modifier = Modifier.fillMaxHeight(),
		) {
			EditorTopBar(component, snackbarHostState)

			Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
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

			//val verticalScrollState = rememberScrollState(0)
			Row {
				RichTextEditor(
					modifier = Modifier
						.fillMaxHeight()
						.widthIn(128.dp, 700.dp)
						.padding(start = Ui.Padding.XL, end = Ui.Padding.XL),
					value = sceneText,
					onValueChange = { rtv ->
						sceneText = rtv
						component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
					},
					textFieldStyle = defaultRichTextFieldStyle().copy(
						placeholder = MR.strings.scene_editor_body_placeholder.get(),
						textColor = MaterialTheme.colorScheme.onBackground,
						placeholderColor = MaterialTheme.colorScheme.onBackground,
						textStyle = MaterialTheme.typography.bodyMedium,
					),
				)

				Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

				/*
				MpScrollBar(
					modifier = Modifier.fillMaxHeight(),
					state = verticalScrollState
				)
				*/
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}

	SaveDraftDialog(state, component) { message ->
		scope.launch { snackbarHostState.showSnackbar(message) }
	}
}