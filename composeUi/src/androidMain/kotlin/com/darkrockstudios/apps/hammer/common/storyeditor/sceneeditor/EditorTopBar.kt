package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdUnsavedBadge
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditorTopBar(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
	onToggleMetadata: () -> Unit,
) {
	val state by component.state.subscribeAsState()
	val title = remember { derivedStateOf { state.sceneItem.name } }
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val screen = LocalScreenCharacteristic.current

	TopBar(
		title = title,
		onClose = component::closeEditor,
		menuItems = state.menuItems
	) {
		val unsaved = state.sceneBuffer?.dirty == true
		if (unsaved) {
			Row(
				modifier = Modifier.width(IntrinsicSize.Min),
				horizontalArrangement = Arrangement.End,
			) {
				HdUnsavedBadge(
					text = Res.string.scene_editor_unsaved_chip.get(),
					modifier = Modifier.align(Alignment.CenterVertically)
				)

				Spacer(modifier = Modifier.weight(1f))

				IconButton(
					onClick = {
						scope.launch {
							component.storeSceneContent()
							scope.launch { rootSnackbar.showSnackbar(strRes.get(Res.string.scene_editor_toast_save_successful)) }
						}
					},
					modifier = Modifier.testTag(SCENE_EDITOR_SAVE_TAG),
				) {
					Icon(
						Icons.Filled.Save,
						contentDescription = Res.string.scene_editor_save_button.get(),
						tint = MaterialTheme.colorScheme.onSurface
					)
				}
			}
		}

		if (screen.windowWidthClass != WindowWidthSizeClass.Compact) {
			IconButton(onClick = onToggleMetadata) {
				Icon(
					Icons.Filled.Info,
					contentDescription = Res.string.scene_editor_metadata_button.get(),
					tint = MaterialTheme.colorScheme.onSurface
				)
			}
		}

		if (screen.windowWidthClass == WindowWidthSizeClass.Expanded) {
			IconButton(onClick = component::enterFocusMode) {
				Icon(
					imageVector = Icons.Default.Fullscreen,
					contentDescription = Res.string.scene_editor_focus_mode_button.get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}
	}

	RenameSceneDialog(state, component)
}

@Composable
private fun RenameSceneDialog(
	state: SceneEditor.State,
	component: SceneEditor,
) {
	val dialogScope = rememberCoroutineScope()
	var editSceneNameValue by rememberSaveable(state.isEditingName, state.sceneItem.id) {
		mutableStateOf(state.sceneItem.name)
	}

	val validation = rememberNameValidation(editSceneNameValue, NameKind.SceneItem)

	val meta = when (state.sceneItem.type) {
		SceneItem.Type.Scene -> "SCENE"
		SceneItem.Type.Group -> "GROUP"
		SceneItem.Type.Root -> "ROOT"
	}

	fun submit() {
		if (validation.isValid) dialogScope.launch { component.changeSceneName(editSceneNameValue) }
	}

	FormDialog(
		visible = state.isEditingName,
		marker = "§ RENAME",
		meta = meta,
		title = Res.string.scene_editor_rename_dialog_title.get(),
		confirmLabel = Res.string.scene_editor_rename_button.get(),
		cancelLabel = Res.string.scene_editor_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = component::endSceneNameEdit,
		onDismiss = component::endSceneNameEdit,
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = editSceneNameValue,
			onValueChange = { editSceneNameValue = it },
			label = Res.string.scene_editor_name_hint.get(),
			autoFocus = true,
			error = validation.fieldError(editSceneNameValue),
			onImeAction = ::submit,
			testTag = RENAME_SCENE_FIELD_TAG,
		)
	}
}
