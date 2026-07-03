package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.TopBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdUnsavedBadge
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneTypeMeta
import com.darkrockstudios.apps.hammer.scene_editor_cancel_button
import com.darkrockstudios.apps.hammer.scene_editor_focus_mode_button
import com.darkrockstudios.apps.hammer.scene_editor_metadata_button
import com.darkrockstudios.apps.hammer.scene_editor_name_hint
import com.darkrockstudios.apps.hammer.scene_editor_rename_button
import com.darkrockstudios.apps.hammer.scene_editor_rename_dialog_title
import com.darkrockstudios.apps.hammer.scene_editor_save_button
import com.darkrockstudios.apps.hammer.scene_editor_toast_save_successful
import com.darkrockstudios.apps.hammer.scene_editor_unsaved_chip
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

	val meta = sceneTypeMeta(state.sceneItem)

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
