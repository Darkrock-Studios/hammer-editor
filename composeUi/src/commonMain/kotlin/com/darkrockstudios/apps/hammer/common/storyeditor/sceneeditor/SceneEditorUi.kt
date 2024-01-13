package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneDeleteDialog
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneEditorUi(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()

	var sceneText by remember {
		mutableStateOf(
			getInitialEditorContent(state.sceneBuffer?.content)
		)
	}

	LaunchedEffect(lastForceUpdate) {
		sceneText = getInitialEditorContent(state.sceneBuffer?.content)
	}

	Toaster(component, rootSnackbar)

	BoxWithConstraints(modifier = modifier) {
		val boxWithConstraintsScope = this

		Column(modifier = Modifier.fillMaxHeight()) {
			EditorTopBar(component, rootSnackbar)

			Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
				EditorAction(
					iconRes = MR.images.icon_bold,
					active = sceneText.currentStyles.contains(Style.Bold),
				) {
					sceneText = sceneText.insertStyle(Style.Bold)
				}
				EditorAction(
					iconRes = MR.images.icon_italic,
					active = sceneText.currentStyles.contains(Style.Italic),
				) {
					sceneText = sceneText.insertStyle(Style.Italic)
				}
				EditorAction(
					iconRes = MR.images.icon_undo,
					active = sceneText.isUndoAvailable
				) {
					sceneText = sceneText.undo()
				}
				EditorAction(
					iconRes = MR.images.icon_redo,
					active = sceneText.isRedoAvailable
				) {
					sceneText = sceneText.redo()
				}

				EditorAction(
					iconRes = MR.images.icon_text_decrease,
					active = false,
				) {
					component.decreaseTextSize()
				}
				EditorAction(
					iconRes = MR.images.icon_text_increase,
					active = false,
				) {
					component.increaseTextSize()
				}
				EditorAction(
					iconRes = MR.images.icon_text_reset,
					active = false,
				) {
					component.resetTextSize()
				}
			}

			//val verticalScrollState = rememberScrollState(0)
			Row(
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.Center
			) {
				RichTextEditor(
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
						.fillMaxHeight()
						.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH)
						.padding(horizontal = Ui.Padding.XL),
					value = sceneText,
					onValueChange = { rtv ->
						sceneText = rtv
						component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
					},
					textFieldStyle = defaultRichTextFieldStyle().copy(
						placeholder = MR.strings.scene_editor_body_placeholder.get(),
						textColor = MaterialTheme.colorScheme.onSurface,
						placeholderColor = MaterialTheme.colorScheme.onSurface,
						textStyle = MaterialTheme.typography.bodyLarge.copy(
							fontSize = state.textSize.sp
						),
					),
				)

				Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

				/*
				MpScrollBar(
					modifier = Modifier.fillMaxHeight(),
					state = verticalScrollState
				)
				*/
				val remainingWidth = remember(boxWithConstraintsScope.maxWidth) {
					boxWithConstraintsScope.maxWidth - TextEditorDefaults.MAX_WIDTH
				}
				SceneMetadataSidebar(component, remainingWidth)
			}
		}
	}

	SaveDraftDialog(state, component) { message ->
		rootSnackbar.showSnackbar(message)
	}

	if (state.confirmDelete) {
		SceneDeleteDialog(state.sceneItem) { doDelete ->
			if (doDelete) {
				component.doDelete()
			} else {
				component.endDelete()
			}
		}
	}
}

@Composable
private fun SceneMetadataSidebar(component: SceneEditor, remainingWidth: Dp) {
	val state by component.state.subscribeAsState()

	if (remainingWidth >= SCENE_METADATA_MIN_WIDTH) {
		AnimatedVisibility(
			visible = state.showMetadata,
			enter = slideInHorizontally { it } + fadeIn(),
			exit = slideOutHorizontally { it } + fadeOut(),
		) {
			Box(modifier = Modifier.padding(Ui.Padding.L)) {
				SceneMetadataPanelUi(
					component = component.sceneMetadataComponent,
					modifier = Modifier.wrapContentWidth().widthIn(max = SCENE_METADATA_MAX_WIDTH).fillMaxHeight(),
					closeMetadata = component::toggleMetadataVisibility,
				)
			}
		}
	} else {
		if (state.showMetadata) {
			Dialog(onDismissRequest = component::toggleMetadataVisibility) {
				Box(modifier = Modifier.padding(Ui.Padding.L)) {
					SceneMetadataPanelUi(
						component = component.sceneMetadataComponent,
						modifier = Modifier.fillMaxWidth().fillMaxHeight(),
						closeMetadata = component::toggleMetadataVisibility,
					)
				}
			}
		}
	}
}