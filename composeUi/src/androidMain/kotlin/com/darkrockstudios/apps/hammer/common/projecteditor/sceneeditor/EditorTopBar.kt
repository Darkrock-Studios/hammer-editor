package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.TopBar
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditorTopBar(component: SceneEditor, snackbarHostState: SnackbarHostState) {
	val state by component.state.subscribeAsState()
	val title = remember { derivedStateOf { state.sceneItem.name } }

	TopBar(
		title = title,
		onClose = component::closeEditor,
		menuItems = state.menuItems
	)

	MpDialog(
		onCloseRequest = component::endSceneNameEdit,
		visible = state.isEditingName,
		title = MR.strings.scene_editor_rename_dialog_title.get()
	) {

		var editSceneNameValue by remember { mutableStateOf(state.sceneItem.name) }
		val scope = rememberCoroutineScope()

		TextField(
			value = editSceneNameValue,
			onValueChange = { editSceneNameValue = it },
			modifier = Modifier.padding(Ui.Padding.XL),
			label = { Text(MR.strings.scene_editor_name_hint.get()) }
		)
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
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
		}
	}
}
