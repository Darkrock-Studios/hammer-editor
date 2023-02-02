package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorUi(
	component: SceneEditor,
	modifier: Modifier = Modifier,
	drawableKlass: Any? = null
) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	val lastDiscarded by component.lastDiscarded.subscribeAsState()

	val snackbarHostState = remember { SnackbarHostState() }
	var sceneText by remember {
		mutableStateOf(
			getInitialEditorContent(state.sceneBuffer?.content)
		)
	}

	LaunchedEffect(lastDiscarded) {
		sceneText = getInitialEditorContent(state.sceneBuffer?.content)
	}

	Box(modifier = modifier) {
		Column(modifier = Modifier.fillMaxSize()) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (state.isEditingName) {
					var editSceneNameValue by remember { mutableStateOf(state.sceneItem.name) }

					TextField(
						value = editSceneNameValue,
						onValueChange = { editSceneNameValue = it },
						modifier = Modifier.padding(Ui.Padding.XL),
						label = { Text("Scene Name") }
					)
					IconButton(onClick = { component.changeSceneName(editSceneNameValue) }) {
						Icon(
							Icons.Filled.Check,
							"Rename",
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
					IconButton(onClick = component::endSceneNameEdit) {
						Icon(
							Icons.Filled.Cancel,
							"Cancel",
							tint = MaterialTheme.colorScheme.error
						)
					}
				} else {
					ClickableText(
						AnnotatedString(state.sceneItem.name),
						modifier = Modifier.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = 24.dp,
							bottom = 28.dp
						),
						onClick = { component.beginSceneNameEdit() },
						style = MaterialTheme.typography.headlineMedium
							.copy(color = MaterialTheme.colorScheme.onBackground),
					)

					val unsaved = state.sceneBuffer?.dirty == true
					if (unsaved) {
						Badge(modifier = Modifier.align(Alignment.Top).padding(top = Ui.Padding.L)) { Text("Unsaved") }

						Spacer(modifier = Modifier.weight(1f))

						IconButton(onClick = {
							component.storeSceneContent()
							scope.launch { snackbarHostState.showSnackbar("Saved") }
						}) {
							Icon(
								Icons.Filled.Save,
								contentDescription = "Save",
								tint = MaterialTheme.colorScheme.onSurface
							)
						}
					}
				}
			}
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
						.padding(Ui.Padding.XL),
					value = sceneText,
					onValueChange = { rtv ->
						sceneText = rtv
						component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
					},
					textFieldStyle = defaultRichTextFieldStyle().copy(
						placeholder = "Begin writing your Scene here",
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