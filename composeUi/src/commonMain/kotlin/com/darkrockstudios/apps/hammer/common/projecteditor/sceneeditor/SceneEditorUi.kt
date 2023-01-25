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
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.painterResource
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.richtexteditor.model.RichTextValue
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle
import kotlinx.coroutines.launch

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
			getInitialContent(state.sceneBuffer?.content)
		)
	}

	LaunchedEffect(lastDiscarded) {
		sceneText = getInitialContent(state.sceneBuffer?.content)
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

			RichTextEditor(
				modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL),
				value = sceneText,
				onValueChange = { rtv ->
					sceneText = rtv
					component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = "Begin writing your Scene here",
					textColor = MaterialTheme.colorScheme.onBackground,
					placeholderColor = MaterialTheme.colorScheme.onBackground,
				)
			)
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}

	SaveDraftDialog(state, component) { message ->
		scope.launch { snackbarHostState.showSnackbar(message) }
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveDraftDialog(
	state: SceneEditor.State,
	component: SceneEditor,
	showSnackbar: (message: String) -> Unit
) {
	var draftName by remember { mutableStateOf("") }

	MpDialog(
		visible = state.isSavingDraft,
		title = "Save Draft:",
		onCloseRequest = {
			component.endSaveDraft()
			draftName = ""
		}
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				TextField(
					value = draftName,
					onValueChange = { draftName = it },
					singleLine = true
				)
				Row {
					Button(onClick = {
						if (component.saveDraft(draftName)) {
							component.endSaveDraft()
							draftName = ""
							showSnackbar("Draft Saved")
						}
					}) {
						Text("Save")
					}
					Button(onClick = {
						component.endSaveDraft()
						draftName = ""
					}) {
						Text("Cancel")
					}
				}
			}
		}
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
			tint = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
			contentDescription = null
		)
	}
}