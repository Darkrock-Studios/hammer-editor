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
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
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
	val strRes = rememberStrRes()
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

	LaunchedEffect(state.toast) {
		state.toast?.let { message ->
			snackbarHostState.showSnackbar(strRes.get(message))
		}
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
						label = { Text(MR.strings.scene_editor_name_hint.get()) }
					)
					IconButton(onClick = { scope.launch { component.changeSceneName(editSceneNameValue) } }) {
						Icon(
							Icons.Filled.Check,
							MR.strings.scene_editor_rename_button.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
					IconButton(onClick = component::endSceneNameEdit) {
						Icon(
							Icons.Filled.Cancel,
							MR.strings.scene_editor_cancel_button.get(),
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
						Badge(
							modifier = Modifier.align(Alignment.Top).padding(top = Ui.Padding.L)
						) { Text(MR.strings.scene_editor_unsaved_chip.get()) }

						Spacer(modifier = Modifier.weight(1f))

						IconButton(onClick = {
							scope.launch {
								component.storeSceneContent()
								scope.launch { snackbarHostState.showSnackbar(strRes.get(MR.strings.scene_editor_toast_save_successful)) }
							}
						}) {
							Icon(
								Icons.Filled.Save,
								contentDescription = MR.strings.scene_editor_save_button.get(),
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